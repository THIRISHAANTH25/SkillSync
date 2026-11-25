package edu.college.smartmatcher.web;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Reads the signed-in student's dashboard using only their authenticated ID. */
public final class StudentDashboardService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public DashboardData load(Connection connection, long studentId) throws SQLException {
        DashboardData data = loadProfile(connection, studentId);
        data.today = DATE.format(LocalDate.now());
        data.activeProjects = scalar(connection,
                "SELECT COUNT(*) FROM Projects WHERE creator_id = ? AND status IN ('OPEN', 'IN_PROGRESS')", studentId);
        data.activeTeams = scalar(connection, "SELECT COUNT(DISTINCT t.team_id) FROM Teams t "
                + "JOIN Projects p ON p.project_id = t.project_id WHERE t.status IN ('FORMING', 'ACTIVE') "
                + "AND (p.creator_id = ? OR EXISTS (SELECT 1 FROM Team_Members tm "
                + "WHERE tm.team_id = t.team_id AND tm.student_id = ?))", studentId, studentId);
        data.pendingInvitations = scalar(connection, "SELECT COUNT(*) FROM Project_Invitations "
                + "WHERE student_id = ? AND status = 'PENDING'", studentId);
        data.completedProjects = scalar(connection, "SELECT COUNT(DISTINCT p.project_id) FROM Projects p "
                + "WHERE p.status = 'COMPLETED' AND (p.creator_id = ? OR EXISTS (SELECT 1 FROM Teams t "
                + "JOIN Team_Members tm ON tm.team_id = t.team_id WHERE t.project_id = p.project_id "
                + "AND tm.student_id = ?))", studentId, studentId);
        loadProjects(connection, studentId, data);
        loadRecommendations(connection, studentId, data);
        loadTeamCoverage(connection, studentId, data);
        loadActivity(connection, studentId, data);
        return data;
    }

    private DashboardData loadProfile(Connection connection, long studentId) throws SQLException {
        DashboardData data = new DashboardData();
        String sql = "SELECT full_name, email, department, year_of_study, bio, availability_status "
                + "FROM Students WHERE student_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("The authenticated student profile was not found.");
                data.name = escape(result.getString("full_name"));
                data.email = escape(result.getString("email"));
                data.department = escape(result.getString("department"));
                data.year = result.getInt("year_of_study");
                data.bio = escape(result.getString("bio"));
                data.availability = result.getString("availability_status");
            }
        }

        String interestsSql = "SELECT i.interest_id, i.interest_name, si.student_id IS NOT NULL AS selected "
                + "FROM Interests i LEFT JOIN Student_Interests si ON si.interest_id = i.interest_id "
                + "AND si.student_id = ? ORDER BY i.interest_name";
        try (PreparedStatement statement = connection.prepareStatement(interestsSql)) {
            statement.setLong(1, studentId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    InterestOption option = new InterestOption(result.getLong("interest_id"),
                            escape(result.getString("interest_name")), result.getBoolean("selected"));
                    data.interestOptions.add(option);
                    if (option.selected) data.interests.add(option.name);
                }
            }
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT sk.skill_name, ss.proficiency_level FROM Student_Skills ss "
                        + "JOIN Skills sk ON sk.skill_id = ss.skill_id WHERE ss.student_id = ? "
                        + "ORDER BY ss.proficiency_level DESC, sk.skill_name LIMIT 8")) {
            statement.setLong(1, studentId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) data.skills.add(new SkillItem(escape(result.getString("skill_name")),
                        result.getInt("proficiency_level")));
            }
        }

        int complete = 4; // Name, email, department, and year are required account fields.
        if (!data.bio.isBlank()) complete++;
        if (!data.interests.isEmpty()) complete++;
        if (!data.skills.isEmpty()) complete++;
        if (data.availability != null && !data.availability.isBlank()) complete++;
        data.profileCompletion = complete * 100 / 8;
        return data;
    }

    private void loadProjects(Connection connection, long studentId, DashboardData data) throws SQLException {
        String sql = "SELECT p.project_id, p.title, p.description, p.status, p.required_team_size, "
                + "COUNT(DISTINCT tm.student_id) + CASE WHEN EXISTS (SELECT 1 FROM Teams own_team "
                + "JOIN Team_Members own_member ON own_member.team_id = own_team.team_id "
                + "WHERE own_team.project_id = p.project_id AND own_team.status IN ('FORMING', 'ACTIVE') "
                + "AND own_member.student_id = p.creator_id) "
                + "THEN 0 ELSE 1 END AS occupied, GROUP_CONCAT(DISTINCT sk.skill_name "
                + "ORDER BY sk.skill_name SEPARATOR ', ') AS skills "
                + "FROM Projects p LEFT JOIN Teams t ON t.project_id = p.project_id AND t.status IN ('FORMING', 'ACTIVE') "
                + "LEFT JOIN Team_Members tm ON tm.team_id = t.team_id "
                + "LEFT JOIN Project_Skills ps ON ps.project_id = p.project_id "
                + "LEFT JOIN Skills sk ON sk.skill_id = ps.skill_id "
                + "WHERE p.creator_id = ? AND p.status IN ('OPEN', 'IN_PROGRESS') "
                + "GROUP BY p.project_id, p.title, p.description, p.status, p.required_team_size "
                + "ORDER BY p.created_at DESC, p.project_id DESC LIMIT 4";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) data.projects.add(new ProjectCard(result.getLong("project_id"),
                        escape(result.getString("title")), escape(result.getString("description")),
                        result.getString("status"), result.getInt("required_team_size"),
                        result.getInt("occupied"), escape(result.getString("skills"))));
            }
        }
    }

    private void loadRecommendations(Connection connection, long studentId, DashboardData data) throws SQLException {
        String sql = "SELECT st.full_name, st.department, st.year_of_study, p.title, mr.matching_score "
                + "FROM Match_Results mr JOIN Projects p ON p.project_id = mr.project_id "
                + "JOIN Students st ON st.student_id = mr.student_id "
                + "WHERE p.creator_id = ? AND p.status = 'OPEN' AND st.is_suspended = FALSE "
                + "AND st.student_id <> ? AND NOT EXISTS (SELECT 1 FROM Teams t JOIN Team_Members tm "
                + "ON tm.team_id = t.team_id WHERE t.project_id = p.project_id AND tm.student_id = st.student_id) "
                + "AND NOT EXISTS (SELECT 1 FROM Project_Invitations pi WHERE pi.project_id = p.project_id "
                + "AND pi.student_id = st.student_id AND pi.status IN ('PENDING', 'ACCEPTED')) "
                + "AND NOT EXISTS (SELECT 1 FROM Project_Applications pa WHERE pa.project_id = p.project_id "
                + "AND pa.student_id = st.student_id AND pa.status IN ('PENDING', 'SHORTLISTED', 'ACCEPTED')) "
                + "ORDER BY mr.matching_score DESC, mr.recommendation_date DESC, st.full_name LIMIT 3";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            statement.setLong(2, studentId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) data.recommendations.add(new DashboardRecommendation(
                        escape(result.getString("full_name")), escape(result.getString("department")),
                        result.getInt("year_of_study"), escape(result.getString("title")),
                        result.getBigDecimal("matching_score").setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()));
            }
        }
    }

    private void loadTeamCoverage(Connection connection, long studentId, DashboardData data) throws SQLException {
        String sql = "SELECT p.title, COUNT(DISTINCT ps.skill_id) AS required_count, "
                + "COUNT(DISTINCT CASE WHEN ss.skill_id IS NOT NULL THEN ps.skill_id END) AS covered_count "
                + "FROM Teams t JOIN Projects p ON p.project_id = t.project_id "
                + "LEFT JOIN Project_Skills ps ON ps.project_id = p.project_id "
                + "LEFT JOIN (SELECT team_id, student_id FROM Team_Members UNION "
                + "SELECT t2.team_id, p2.creator_id AS student_id FROM Teams t2 "
                + "JOIN Projects p2 ON p2.project_id = t2.project_id) participants ON participants.team_id = t.team_id "
                + "LEFT JOIN Student_Skills ss ON ss.student_id = participants.student_id AND ss.skill_id = ps.skill_id "
                + "WHERE p.status <> 'REMOVED' AND (p.creator_id = ? OR EXISTS (SELECT 1 FROM Team_Members mine "
                + "WHERE mine.team_id = t.team_id AND mine.student_id = ?)) "
                + "GROUP BY t.team_id, p.title ORDER BY t.created_at DESC LIMIT 3";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            statement.setLong(2, studentId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int required = result.getInt("required_count");
                    int covered = result.getInt("covered_count");
                    data.teamCoverage.add(new TeamCoverage(escape(result.getString("title")), required, covered,
                            required == 0 ? 0 : covered * 100 / required));
                }
            }
        }
    }

    private void loadActivity(Connection connection, long studentId, DashboardData data) throws SQLException {
        String sql = "SELECT activity, occurred_at FROM ("
                + "SELECT CONCAT('Created project: ', title) AS activity, created_at AS occurred_at FROM Projects "
                + "WHERE creator_id = ? AND status <> 'REMOVED' "
                + "UNION ALL SELECT CONCAT('Received an invitation: ', p.title), i.invited_at "
                + "FROM Project_Invitations i JOIN Projects p ON p.project_id = i.project_id WHERE i.student_id = ? "
                + "AND p.status <> 'REMOVED' "
                + "UNION ALL SELECT CONCAT('Requested to join: ', p.title), a.application_date "
                + "FROM Project_Applications a JOIN Projects p ON p.project_id = a.project_id WHERE a.student_id = ? "
                + "AND p.status <> 'REMOVED' "
                + "UNION ALL SELECT CONCAT('Joined team: ', t.team_name), tm.joined_at FROM Team_Members tm "
                + "JOIN Teams t ON t.team_id = tm.team_id JOIN Projects p ON p.project_id = t.project_id "
                + "WHERE tm.student_id = ? AND p.status <> 'REMOVED') recent "
                + "ORDER BY occurred_at DESC LIMIT 6";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 1; i <= 4; i++) statement.setLong(i, studentId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Timestamp timestamp = result.getTimestamp("occurred_at");
                    data.activity.add(new ActivityItem(escape(result.getString("activity")),
                            timestamp == null ? "" : DATE.format(timestamp.toLocalDateTime())));
                }
            }
        }
    }

    private long scalar(Connection connection, String sql, long... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) statement.setLong(i + 1, parameters[i]);
            try (ResultSet result = statement.executeQuery()) { return result.next() ? result.getLong(1) : 0L; }
        }
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    public static final class DashboardData {
        String name, email, department, bio, availability, today;
        int year, profileCompletion;
        long activeProjects, activeTeams, pendingInvitations, completedProjects;
        final List<InterestOption> interestOptions = new ArrayList<>();
        final List<String> interests = new ArrayList<>();
        final List<SkillItem> skills = new ArrayList<>();
        final List<ProjectCard> projects = new ArrayList<>();
        final List<DashboardRecommendation> recommendations = new ArrayList<>();
        final List<TeamCoverage> teamCoverage = new ArrayList<>();
        final List<ActivityItem> activity = new ArrayList<>();
        public String getName(){return name;} public String getEmail(){return email;} public String getDepartment(){return department;}
        public String getBio(){return bio;} public String getAvailability(){return availability;} public String getToday(){return today;}
        public int getYear(){return year;} public int getProfileCompletion(){return profileCompletion;}
        public long getActiveProjects(){return activeProjects;} public long getActiveTeams(){return activeTeams;}
        public long getPendingInvitations(){return pendingInvitations;} public long getCompletedProjects(){return completedProjects;}
        public List<InterestOption> getInterestOptions(){return interestOptions;} public List<String> getInterests(){return interests;}
        public List<SkillItem> getSkills(){return skills;} public List<ProjectCard> getProjects(){return projects;}
        public List<DashboardRecommendation> getRecommendations(){return recommendations;}
        public List<TeamCoverage> getTeamCoverage(){return teamCoverage;} public List<ActivityItem> getActivity(){return activity;}
        public String getInitial(){return name == null || name.isBlank() ? "S" : name.substring(0, 1).toUpperCase(java.util.Locale.ROOT);}
    }

    public static final class InterestOption {
        private final long id; private final String name; private final boolean selected;
        InterestOption(long id, String name, boolean selected){this.id=id;this.name=name;this.selected=selected;}
        public long getId(){return id;} public String getName(){return name;} public boolean isSelected(){return selected;}
    }
    public static final class SkillItem {
        private final String name; private final int proficiency;
        SkillItem(String name,int proficiency){this.name=name;this.proficiency=proficiency;}
        public String getName(){return name;} public int getProficiency(){return proficiency;}
        public int getPercent(){return Math.max(0,Math.min(100,proficiency*20));}
        public String getLevel(){return proficiency <= 2 ? "Beginner" : proficiency <= 4 ? "Intermediate" : "Advanced";}
    }
    public static final class ProjectCard {
        private final long id; private final String title, description, status, skills;
        private final int capacity, occupied;
        ProjectCard(long id,String title,String description,String status,int capacity,int occupied,String skills){
            this.id=id;this.title=title;this.description=description;this.status=status;this.capacity=capacity;
            this.occupied=Math.min(occupied,capacity);this.skills=skills;
        }
        public long getId(){return id;} public String getTitle(){return title;} public String getDescription(){return description;}
        public String getStatus(){return status;} public String getSkills(){return skills;} public int getCapacity(){return capacity;}
        public int getOccupied(){return occupied;} public int getPercent(){return capacity == 0 ? 0 : occupied*100/capacity;}
    }
    public static final class DashboardRecommendation {
        private final String name, department, project, score; private final int year;
        DashboardRecommendation(String name,String department,int year,String project,String score){
            this.name=name;this.department=department;this.year=year;this.project=project;this.score=score;
        }
        public String getName(){return name;} public String getDepartment(){return department;} public String getProject(){return project;}
        public int getYear(){return year;} public String getScore(){return score;}
    }
    public static final class TeamCoverage {
        private final String project; private final int required, covered, percent;
        TeamCoverage(String project,int required,int covered,int percent){
            this.project=project;this.required=required;this.covered=covered;this.percent=percent;
        }
        public String getProject(){return project;} public int getRequired(){return required;} public int getCovered(){return covered;}
        public int getPercent(){return percent;}
    }
    public static final class ActivityItem {
        private final String activity, date;
        ActivityItem(String activity,String date){this.activity=activity;this.date=date;}
        public String getActivity(){return activity;} public String getDate(){return date;}
    }
}
