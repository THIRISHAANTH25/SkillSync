# Export the ten legacy tables from the live schema, restore them into a new
# isolated test schema, and compare row counts, foreign keys, and table checksums.
# This script never modifies or drops objects in smart_project_matcher.
#
# Prerequisite: configure a local MySQL login path once, outside this repository:
#   mysql_config_editor set --login-path=skillsync-local --host=127.0.0.1 --user=YOUR_DB_USER --password
# MySQL prompts for the password; it is not saved in this script or source control.

[CmdletBinding()]
param(
    [string]$LoginPath = "skillsync-local",
    [string]$MySqlBinDirectory = "C:\Program Files\MySQL\MySQL Server 8.0\bin"
)

$ErrorActionPreference = "Stop"

$sourceDatabase = "smart_project_matcher"
$archiveDatabase = "smart_project_matcher_legacy_archive_test"
$legacyTables = @(
    "student", "skill", "student_skill", "interest", "student_interest",
    "availability", "project", "project_requirement", "team", "team_member"
)
$currentTables = @(
    "Students", "Skills", "Student_Skills", "Interests", "Student_Interests",
    "Projects", "Project_Skills", "Project_Applications", "Project_Invitations",
    "Teams", "Team_Members", "Match_Results", "Feedback", "Moderation_Audit_Log"
)
$archiveFile = Join-Path $PSScriptRoot "legacy_tables_archive.sql"
$partialArchiveFile = "$archiveFile.partial"
$verifyFile = Join-Path $PSScriptRoot "legacy_archive_verify.sql"
$mysqlExe = Join-Path $MySqlBinDirectory "mysql.exe"
$mysqldumpExe = Join-Path $MySqlBinDirectory "mysqldump.exe"

if (-not (Test-Path -LiteralPath $mysqlExe) -or -not (Test-Path -LiteralPath $mysqldumpExe)) {
    throw "mysql.exe and mysqldump.exe were not found in '$MySqlBinDirectory'. Set -MySqlBinDirectory to your MySQL 8 bin folder."
}
if (-not (Test-Path -LiteralPath $verifyFile)) {
    throw "Missing verification SQL file: $verifyFile"
}
if ((Test-Path -LiteralPath $archiveFile) -or (Test-Path -LiteralPath $partialArchiveFile)) {
    throw "An archive output already exists. Move it safely or choose a new output name before rerunning; this script will not overwrite it."
}

function ConvertTo-NativeArgumentString {
    param([string[]]$Arguments)
    return (($Arguments | ForEach-Object { '"' + $_.Replace('"', '\"') + '"' }) -join ' ')
}

function Invoke-MySqlText {
    param([string[]]$Arguments)
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $mysqlExe
    $startInfo.Arguments = ConvertTo-NativeArgumentString $Arguments
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true

    $process = [System.Diagnostics.Process]::Start($startInfo)
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $process.WaitForExit()
    $stdout = $stdoutTask.GetAwaiter().GetResult()
    $stderr = $stderrTask.GetAwaiter().GetResult()

    if ($process.ExitCode -ne 0) {
        throw "MySQL command failed (exit $($process.ExitCode)). $($stderr.Trim())"
    }
    if (-not [string]::IsNullOrWhiteSpace($stderr)) {
        throw "MySQL emitted a warning/error on stderr; refusing to parse its output. $($stderr.Trim())"
    }

    return @([regex]::Split($stdout, "`r?`n") | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne "" })
}

function Get-MySqlRows {
    param([string]$Sql)
    return Invoke-MySqlText @(
        "--login-path=$LoginPath", "--protocol=tcp", "--host=127.0.0.1", "--port=3306",
        "--database=$sourceDatabase", "--batch", "--raw", "--skip-column-names", "--execute", $Sql
    )
}

function Get-MySqlScalar {
    param([string]$Sql, [string]$Description)
    $lines = @(Get-MySqlRows $Sql)
    if ($lines.Count -ne 1 -or $lines[0] -cnotmatch '^\d+$') {
        $received = if ($lines.Count -eq 0) { "<no output>" } else { $lines -join " | " }
        throw "Expected exactly one numeric result for $Description from '$sourceDatabase'; received: $received"
    }
    return [long]::Parse($lines[0], [Globalization.CultureInfo]::InvariantCulture)
}

function Invoke-MySqlFile {
    param([string]$FilePath, [string]$Database)
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $mysqlExe
    $startInfo.Arguments = ConvertTo-NativeArgumentString @(
        "--login-path=$LoginPath", "--protocol=tcp", "--host=127.0.0.1", "--port=3306",
        "--default-character-set=utf8mb4", "--database=$Database"
    )
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $process = [System.Diagnostics.Process]::Start($startInfo)
    $inputFile = [System.IO.File]::OpenRead($FilePath)
    try {
        $inputFile.CopyTo($process.StandardInput.BaseStream)
    } finally {
        $inputFile.Dispose()
        $process.StandardInput.Close()
    }
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) {
        throw "MySQL import failed (exit $($process.ExitCode)) for '$FilePath'. The test database may be incomplete; do not use it to authorize cleanup."
    }
}

function Get-TableCounts {
    param([string]$Database, [string[]]$Tables)
    $parts = foreach ($table in $Tables) {
        "SELECT '$table', COUNT(*) FROM ``$Database``.``$table``"
    }
    $sql = $parts -join " UNION ALL "
    $rows = @(Get-MySqlRows $sql)
    if ($rows.Count -ne $Tables.Count) {
        throw "Expected $($Tables.Count) table-count rows from '$Database'; received $($rows.Count)."
    }
    $counts = @{}
    foreach ($row in $rows) {
        $pair = $row -split "`t"
        if ($pair.Count -ne 2 -or $pair[1] -cnotmatch '^\d+$' -or $pair[0] -notin $Tables) {
            throw "Unexpected row-count output from MySQL for '$Database': $row"
        }
        if ($counts.ContainsKey($pair[0])) { throw "Duplicate row-count output for '$Database.$($pair[0])'." }
        $counts[$pair[0]] = [long]$pair[1]
    }
    if ($counts.Count -ne $Tables.Count) { throw "Could not read a row count for every expected table in '$Database'." }
    return $counts
}

function Invoke-MySqlDump {
    param([string]$OutputPath)
    $arguments = @(
        "--login-path=$LoginPath", "--protocol=tcp", "--host=127.0.0.1", "--port=3306",
        "--default-character-set=utf8mb4", "--single-transaction", "--skip-lock-tables",
        "--skip-add-drop-table", "--no-tablespaces", "--set-gtid-purged=OFF", "--hex-blob", "--triggers",
        $sourceDatabase
    ) + $legacyTables
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $mysqldumpExe
    $startInfo.Arguments = ConvertTo-NativeArgumentString $arguments
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $process = [System.Diagnostics.Process]::Start($startInfo)
    $outputFile = [System.IO.File]::Create($OutputPath)
    try {
        $process.StandardOutput.BaseStream.CopyTo($outputFile)
    } finally {
        $outputFile.Dispose()
    }
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) {
        throw "mysqldump failed (exit $($process.ExitCode)). A partial dump is left at '$OutputPath.partial'; do not use it for cleanup."
    }
}

$legacySqlList = ($legacyTables | ForEach-Object { "'$_'" }) -join ","
$currentSqlList = ($currentTables | ForEach-Object { "'$_'" }) -join ","

$legacyTableCount = Get-MySqlScalar "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$sourceDatabase' AND TABLE_TYPE='BASE TABLE' AND TABLE_NAME IN ($legacySqlList)" "legacy table count"
$currentTableCount = Get-MySqlScalar "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$sourceDatabase' AND TABLE_TYPE='BASE TABLE' AND TABLE_NAME IN ($currentSqlList)" "current table count"
$allSourceTableCount = Get-MySqlScalar "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$sourceDatabase' AND TABLE_TYPE='BASE TABLE'" "total base table count"
if ($legacyTableCount -ne 10 -or $currentTableCount -ne 14 -or $allSourceTableCount -ne 24) {
    throw "Source preflight failed: found $legacyTableCount/10 legacy tables, $currentTableCount/14 current tables, and $allSourceTableCount/24 total base tables. No archive was created."
}

$outsideForeignKeys = Get-MySqlScalar "SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA='$sourceDatabase' AND TABLE_NAME IN ($legacySqlList) AND REFERENCED_TABLE_NAME IS NOT NULL AND (REFERENCED_TABLE_SCHEMA <> '$sourceDatabase' OR REFERENCED_TABLE_NAME NOT IN ($legacySqlList))" "external legacy foreign-key count"
if ($outsideForeignKeys -ne 0) {
    throw "Archive preflight found $outsideForeignKeys legacy foreign-key column(s) pointing outside the ten legacy tables. Inspect dependencies and include needed parent tables before continuing. No archive was created."
}

$sourceCounts = Get-TableCounts $sourceDatabase $legacyTables
$sourceTotal = [long](($sourceCounts.Values | Measure-Object -Sum).Sum)
if ($sourceTotal -ne 48) {
    throw "Source legacy row total is $sourceTotal, not the expected 48. No archive was created."
}

$targetExists = Get-MySqlScalar "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='$archiveDatabase'" "archive schema existence check"
if ($targetExists -ne 0) {
    throw "The test schema '$archiveDatabase' already exists. It was not changed. Choose a new test schema name after reviewing the existing one."
}

Write-Host "Source checks passed: 10 legacy tables, 14 current tables, 24 total tables, 48 legacy rows."
Write-Host "Exporting the ten legacy tables to $archiveFile ..."
Invoke-MySqlDump $partialArchiveFile
Move-Item -LiteralPath $partialArchiveFile -Destination $archiveFile

Invoke-MySqlText @(
    "--login-path=$LoginPath", "--protocol=tcp", "--host=127.0.0.1", "--port=3306",
    "--execute", "CREATE DATABASE ``$archiveDatabase`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
) | Out-Null

Write-Host "Restoring the archive into the separate schema '$archiveDatabase' ..."
Invoke-MySqlFile $archiveFile $archiveDatabase
Invoke-MySqlFile $verifyFile $archiveDatabase

$archiveCounts = Get-TableCounts $archiveDatabase $legacyTables
foreach ($table in $legacyTables) {
    if ($sourceCounts[$table] -ne $archiveCounts[$table]) {
        throw "Row-count mismatch for '$table': source=$($sourceCounts[$table]), archive=$($archiveCounts[$table]). Do not run the cleanup script."
    }
}
$archiveTotal = [long](($archiveCounts.Values | Measure-Object -Sum).Sum)
if ($archiveTotal -ne 48) { throw "Restored archive has $archiveTotal rows, not 48. Do not run the cleanup script." }

$checksumPairs = foreach ($table in $legacyTables) {
    "$sourceDatabase.$table, $archiveDatabase.$table"
}
$checksumRows = Get-MySqlRows ("CHECKSUM TABLE " + ($checksumPairs -join ","))
$checksums = @{}
foreach ($row in $checksumRows) {
    $pair = $row -split "`t"
    if ($pair.Count -ne 2 -or $pair[1] -eq "NULL") { throw "Could not compute a table checksum: $row. Do not run the cleanup script." }
    $checksums[$pair[0]] = $pair[1]
}
foreach ($table in $legacyTables) {
    if ($checksums["$sourceDatabase.$table"] -ne $checksums["$archiveDatabase.$table"]) {
        throw "Content checksum mismatch for '$table'. Do not run the cleanup script."
    }
}

$sourceFkCount = Get-MySqlScalar "SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA='$sourceDatabase' AND TABLE_NAME IN ($legacySqlList) AND REFERENCED_TABLE_NAME IS NOT NULL" "source legacy foreign-key column count"
$archiveFkCount = Get-MySqlScalar "SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA='$archiveDatabase' AND TABLE_NAME IN ($legacySqlList) AND REFERENCED_TABLE_NAME IS NOT NULL" "archive foreign-key column count"
if ($sourceFkCount -ne $archiveFkCount) {
    throw "Foreign-key column count mismatch: source=$sourceFkCount, archive=$archiveFkCount. Do not run the cleanup script."
}

Write-Host "ARCHIVE VERIFIED: all 10 legacy tables restored; 48 rows; per-table counts and checksums match; foreign-key column counts match."
Write-Host "The source schema was read only. The 14 current application tables were not changed."
Write-Host "Review the output and checksum rows before manually running cleanup_legacy_tables.sql."
