<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <script src="${pageContext.request.contextPath}/assets/theme.js?v=20261029-universal-login"></script>
    
    <title>Student registration | SkillSync — Smart Project Team Matcher</title>
    
    <link rel="icon" type="image/png" href="${pageContext.request.contextPath}/assets/skillsync-favicon.png?v=20261018-phase1">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/theme.css?v=20261029-universal-login">
</head>
<body class="auth-page auth-page-register">
<nav class="appearance-nav" aria-label="Appearance settings"><%@ include file="theme-toggle.jspf" %></nav>
<main class="auth-layout auth-layout-register">
<section class="auth-story" aria-label="About Smart Project Matcher">
    <a class="auth-brand" href="${pageContext.request.contextPath}/login" aria-label="SkillSync — Smart Project Team Matcher">
        <img class="auth-logo-lockup auth-logo-lockup-register" data-theme-logo data-logo-dark-src="${pageContext.request.contextPath}/assets/skillsync-logo-lockup-transparent.png?v=20261018-transparent1" data-logo-light-src="${pageContext.request.contextPath}/assets/skillsync-logo-lockup-light.png?v=20261018-transparent1" src="${pageContext.request.contextPath}/assets/skillsync-logo-lockup-transparent.png?v=20261018-transparent1" alt="SkillSync — Where skills meet opportunities">
        <span class="auth-brand-caption">Smart Project Team Matcher</span>
    </a>
    <span class="eyebrow">YOUR NEXT TEAM STARTS HERE</span>
    <h2>Make your skills part of something bigger.</h2>
    <p>Create a student profile, share what you do best, and discover open project teams.</p>
    <div class="auth-story-note"><span aria-hidden="true">✳</span><span>A clearer way to connect student skills with project opportunities.</span></div>
</section>
<section class="auth-card">
<div class="auth-card-heading"><span class="eyebrow">GET STARTED</span><h1>Create your account</h1><p>Set up your student profile to join the workspace.</p></div>
<% if (request.getAttribute("message") != null) { %>
<p class="message" role="alert"><%= request.getAttribute("message") %></p>
<% } %>
<form method="post" action="${pageContext.request.contextPath}/register">
    <label>Full name <input name="full_name" maxlength="160" autocomplete="name" required></label>
    <label>College email <input type="email" name="email" maxlength="254" autocomplete="email" required></label>
    <label>Password (8–72 UTF-8 bytes) <span class="password-input"><input type="password" name="password" minlength="8" autocomplete="new-password" required><button type="button" class="password-toggle" data-password-toggle aria-label="Show password">Show</button></span></label>
    <label>Department <input name="department" maxlength="120" required></label>
    <label>Year of study <input type="number" name="year_of_study" min="1" max="8" required></label>
    <button type="submit">Register</button>
</form>
<p class="auth-switch">Already registered? <a href="${pageContext.request.contextPath}/login">Sign in</a>.</p>
</section>
</main>
</body>
</html>
