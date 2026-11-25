<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <script src="${pageContext.request.contextPath}/assets/theme.js?v=20261029-universal-login"></script>
    <title>Login | SkillSync — Smart Project Team Matcher</title>
    <link rel="icon" type="image/png" href="${pageContext.request.contextPath}/assets/skillsync-favicon.png?v=20261018-phase1">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/theme.css?v=20261029-universal-login">
</head>
<body class="auth-page auth-page-login">
<nav class="appearance-nav" aria-label="Appearance settings"><%@ include file="theme-toggle.jspf" %></nav>
<main class="auth-layout">
<section class="auth-story" aria-label="About Smart Project Matcher">
    <a class="auth-brand" href="${pageContext.request.contextPath}/login" aria-label="SkillSync, Smart Project Team Matcher">
        <img class="auth-logo-lockup" data-theme-logo data-logo-dark-src="${pageContext.request.contextPath}/assets/skillsync-logo-lockup-transparent.png?v=20261018-transparent1" data-logo-light-src="${pageContext.request.contextPath}/assets/skillsync-logo-lockup-light.png?v=20261018-transparent1" src="${pageContext.request.contextPath}/assets/skillsync-logo-lockup-transparent.png?v=20261018-transparent1" alt="SkillSync — Where skills meet opportunities">
        <span class="auth-brand-caption">Smart Project Team Matcher</span>
    </a>
    <span class="eyebrow">BUILD BETTER TEAMS</span>
    <h2>Great projects start with the <span>right people.</span></h2>
    <p>Bring student skills and project ideas together in teams built to do meaningful work.</p>
    <div class="auth-story-note"><span aria-hidden="true">↗</span><span>Find your next project and the teammates who can help make it happen.</span></div>
</section>
<section class="auth-card">
<div class="auth-card-heading"><h1>Welcome back.</h1><p>Sign in to continue to your projects and teams.</p></div>
<% if (request.getAttribute("message") != null) { %>
<p class="message" role="alert"><%= request.getAttribute("message") %></p>
<% } else if ("1".equals(request.getParameter("registered"))) { %>
<p class="success" role="status">Registration complete. You can now sign in.</p>
<% } else if ("1".equals(request.getParameter("loggedOut"))) { %>
<p class="success" role="status">You have been signed out.</p>
<% } %>
<form method="post" action="${pageContext.request.contextPath}/login" class="login-form">
    <label><span>Email address</span><span class="icon-input"><span aria-hidden="true">✉</span><input type="email" name="email" maxlength="254" autocomplete="email" placeholder="you@example.com" required></span></label>
    <label><span>Password</span><span class="password-input icon-input"><span aria-hidden="true">▣</span><input type="password" name="password" autocomplete="current-password" placeholder="Enter your password" required><button type="button" class="password-toggle" data-password-toggle aria-label="Show password">Show</button></span></label>
    <button type="submit">Sign In <span aria-hidden="true">→</span></button>
</form>
<p class="auth-switch">New here? <a href="${pageContext.request.contextPath}/register">Create an account</a>.</p>
</section>
</main>
</body>
</html>
