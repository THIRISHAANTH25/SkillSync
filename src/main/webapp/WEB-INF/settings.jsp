<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <script src="${pageContext.request.contextPath}/assets/theme.js?v=20261029-universal-login"></script>
    <title>Settings | Smart Project Team Matcher</title>
    <link rel="icon" type="image/png" href="${pageContext.request.contextPath}/assets/skillsync-favicon.png?v=20261018-phase1">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/theme.css?v=20261029-universal-login">
</head>
<body>
<div class="shell">
    <header class="topbar"><strong>Smart Project Team Matcher</strong><span>Student workspace</span></header>
    <jsp:include page="/WEB-INF/sidebar.jsp"><jsp:param name="active" value="settings"/></jsp:include>
    <main>
        <h1>Settings</h1>
        <p class="intro">Personalize how the project matcher looks and update your account password.</p>
        <% if (request.getAttribute("settingsSuccess") != null) { %>
        <p class="message" role="status"><%= request.getAttribute("settingsSuccess") %></p>
        <% } %>
        <% if (request.getAttribute("settingsError") != null) { %>
        <p class="error" role="alert"><%= request.getAttribute("settingsError") %></p>
        <% } %>

        <section class="settings-card" aria-labelledby="appearance-heading">
            <h2 id="appearance-heading">Website appearance</h2>
            <div class="settings-grid">
                <fieldset>
                    <legend>Choose a theme</legend>
                    <div class="choice-list">
                        <label class="theme-choice"><input type="radio" name="theme" value="midnight-purple" data-theme-choice><span class="theme-preview" style="--preview-bg:#100B1B;--preview-card:#1B1429;--preview-accent:#A78BFA"><i></i><b></b><em></em></span>Midnight Purple</label>
                        <label class="theme-choice"><input type="radio" name="theme" value="ocean-blue" data-theme-choice><span class="theme-preview" style="--preview-bg:#071421;--preview-card:#102539;--preview-accent:#38BDF8"><i></i><b></b><em></em></span>Ocean Blue</label>
                        <label class="theme-choice"><input type="radio" name="theme" value="emerald-green" data-theme-choice><span class="theme-preview" style="--preview-bg:#081812;--preview-card:#10271E;--preview-accent:#34D399"><i></i><b></b><em></em></span>Emerald Green</label>
                        <label class="theme-choice"><input type="radio" name="theme" value="crimson-red" data-theme-choice><span class="theme-preview" style="--preview-bg:#1B0B0E;--preview-card:#2B151B;--preview-accent:#FB7185"><i></i><b></b><em></em></span>Crimson Red</label>
                        <label class="theme-choice"><input type="radio" name="theme" value="light" data-theme-choice><span class="theme-preview" style="--preview-bg:#F4F7FB;--preview-card:#FFFFFF;--preview-accent:#1457A6"><i></i><b></b><em></em></span>Light</label>
                        <label class="theme-choice"><input type="radio" name="theme" value="amoled-black" data-theme-choice><span class="theme-preview" style="--preview-bg:#000000;--preview-card:#080808;--preview-accent:#8B5CF6"><i></i><b></b><em></em></span>AMOLED Black</label>
                    </div>
                </fieldset>
                <fieldset>
                    <legend>Choose a font</legend>
                    <div class="choice-list">
                        <label class="font-choice"><input type="radio" name="font" value="Inter" data-font-choice>Inter</label>
                        <label class="font-choice"><input type="radio" name="font" value="Poppins" data-font-choice>Poppins</label>
                        <label class="font-choice"><input type="radio" name="font" value="Roboto" data-font-choice>Roboto</label>
                        <label class="font-choice"><input type="radio" name="font" value="Montserrat" data-font-choice>Montserrat</label>
                        <label class="font-choice"><input type="radio" name="font" value="Nunito" data-font-choice>Nunito</label>
                    </div>
                    <p class="font-preview" data-font-preview>Teamwork starts with the right combination of skills.</p>
                </fieldset>
            </div>
            <div class="settings-actions">
                <button type="button" data-save-preferences>Save Preferences</button>
                <button type="button" class="secondary" data-reset-preferences>Reset to Default</button>
            </div>
            <p class="settings-note" data-preferences-status aria-live="polite"></p>
        </section>

        <section class="settings-card" aria-labelledby="password-heading">
            <h2 id="password-heading">Change password</h2>
            <p class="intro">Use a password of at least 8 characters and no more than 72 UTF-8 bytes.</p>
            <form class="password-form" method="post" action="${pageContext.request.contextPath}/settings" autocomplete="on">
                <input type="hidden" name="csrf_token" value="${csrfToken}">
                <label>Current password
                    <span class="password-input"><input type="password" name="current_password" autocomplete="current-password" required><button type="button" class="password-toggle" data-password-toggle aria-label="Show password">Show</button></span>
                </label>
                <label>New password
                    <span class="password-input"><input type="password" name="new_password" minlength="8" autocomplete="new-password" required><button type="button" class="password-toggle" data-password-toggle aria-label="Show password">Show</button></span>
                </label>
                <label>Confirm new password
                    <span class="password-input"><input type="password" name="confirm_password" minlength="8" autocomplete="new-password" required><button type="button" class="password-toggle" data-password-toggle aria-label="Show password">Show</button></span>
                </label>
                <button type="submit">Update Password</button>
            </form>
        </section>
    </main>
</div>
</body>
</html>
