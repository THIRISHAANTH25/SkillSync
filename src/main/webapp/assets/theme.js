/* Apply saved appearance choices before the page can render. */
(function () {
    "use strict";

    var themeKey = "smartProjectMatcherTheme";
    var fontKey = "smartProjectMatcherFont";
    var themes = ["midnight-purple", "ocean-blue", "emerald-green", "crimson-red", "light", "amoled-black"];
    var fonts = ["Inter", "Poppins", "Roboto", "Montserrat", "Nunito"];
    var theme = "midnight-purple";
    var font = "Inter";

    try {
        var oldTheme = window.localStorage.getItem(themeKey);
        if (oldTheme === "dark") oldTheme = "midnight-purple";
        if (oldTheme === "light") oldTheme = "light";
        if (themes.indexOf(oldTheme) !== -1) theme = oldTheme;
        var oldFont = window.localStorage.getItem(fontKey);
        if (fonts.indexOf(oldFont) !== -1) font = oldFont;
    } catch (ignored) {
        // Defaults continue to work when browser storage is disabled.
    }

    var root = document.documentElement;
    root.setAttribute("data-theme", theme);
    root.setAttribute("data-font", font.toLowerCase());
    root.style.setProperty("--site-font", '"' + font + '"');

    // Request only the selected family, with swap behavior and system fallbacks.
    var preconnect = document.createElement("link");
    preconnect.rel = "preconnect";
    preconnect.href = "https://fonts.googleapis.com";
    document.head.appendChild(preconnect);
    var preconnectStatic = document.createElement("link");
    preconnectStatic.rel = "preconnect";
    preconnectStatic.href = "https://fonts.gstatic.com";
    preconnectStatic.crossOrigin = "anonymous";
    document.head.appendChild(preconnectStatic);
    var fontStyles = document.createElement("link");
    fontStyles.rel = "stylesheet";
    fontStyles.href = "https://fonts.googleapis.com/css2?family=" + encodeURIComponent(font) + ":wght@400;500;600;700&display=swap";
    document.head.appendChild(fontStyles);

    function applyTheme(nextTheme) {
        if (themes.indexOf(nextTheme) !== -1) {
            theme = nextTheme;
            root.setAttribute("data-theme", theme);
            syncLogoAssets();
        }
    }

    function applyFont(nextFont) {
        if (fonts.indexOf(nextFont) !== -1) {
            font = nextFont;
            root.setAttribute("data-font", font.toLowerCase());
            root.style.setProperty("--site-font", '"' + font + '"');
            fontStyles.href = "https://fonts.googleapis.com/css2?family=" + encodeURIComponent(font) + ":wght@400;500;600;700&display=swap";
        }
    }

    function syncControls() {
        document.querySelectorAll("[data-theme-choice]").forEach(function (control) {
            control.checked = control.value === theme;
            var choice = control.closest(".theme-choice");
            if (choice) choice.classList.toggle("is-selected", control.checked);
        });
        document.querySelectorAll("[data-font-choice]").forEach(function (control) {
            control.checked = control.value === font;
            var choice = control.closest(".font-choice");
            if (choice) choice.classList.toggle("is-selected", control.checked);
        });
        document.querySelectorAll("[data-font-preview]").forEach(function (preview) {
            preview.style.fontFamily = '"' + font + '", system-ui, sans-serif';
        });
        document.querySelectorAll("[data-theme-toggle]").forEach(function (button) {
            var isLight = theme === "light";
            button.setAttribute("aria-pressed", String(!isLight));
            button.setAttribute("aria-label", isLight ? "Switch to Midnight Purple theme" : "Switch to Light theme");
            button.setAttribute("title", isLight ? "Switch to Midnight Purple theme" : "Switch to Light theme");
            var icon = button.querySelector("[data-theme-icon]");
            var label = button.querySelector("[data-theme-label]");
            if (icon) icon.textContent = isLight ? "☾" : "☀";
            if (label) label.textContent = isLight ? "Midnight Purple" : "Light mode";
        });
    }

    // White lettering in the exact logo crop needs a dark-ink source on light pages.
    // Both files are derived from the supplied logo; dark themes retain the original white lettering.
    function syncLogoAssets() {
        document.querySelectorAll("img[data-theme-logo]").forEach(function (image) {
            var source = theme === "light" ? image.getAttribute("data-logo-light-src")
                : image.getAttribute("data-logo-dark-src");
            if (!source) return;
            if (image.getAttribute("data-applied-logo") === source) {
                image.setAttribute("data-logo-ready", "true");
                return;
            }
            if (theme === "light") image.removeAttribute("data-logo-ready");
            image.setAttribute("data-applied-logo", source);
            image.onload = function () { image.setAttribute("data-logo-ready", "true"); };
            image.onerror = function () { image.setAttribute("data-logo-ready", "true"); };
            image.src = source;
            if (image.complete && image.naturalWidth > 0) image.setAttribute("data-logo-ready", "true");
        });
    }

    function icon(paths) {
        var svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
        svg.setAttribute("viewBox", "0 0 24 24");
        svg.setAttribute("width", "20");
        svg.setAttribute("height", "20");
        svg.setAttribute("fill", "none");
        svg.setAttribute("stroke", "currentColor");
        svg.setAttribute("stroke-width", "1.8");
        svg.setAttribute("stroke-linecap", "round");
        svg.setAttribute("stroke-linejoin", "round");
        svg.setAttribute("aria-hidden", "true");
        paths.forEach(function (pathData) {
            var path = document.createElementNS("http://www.w3.org/2000/svg", "path");
            path.setAttribute("d", pathData);
            svg.appendChild(path);
        });
        return svg;
    }

    // Find shell elements as direct children without relying on :scope support.
    function directChild(parent, selector) {
        for (var index = 0; index < parent.children.length; index++) {
            var child = parent.children[index];
            if (child.matches && child.matches(selector)) return child;
        }
        return null;
    }

    function mountAppShell() {
        var shell = document.querySelector(".shell");
        if (!shell) return;
        var header = directChild(shell, ".topbar");
        var nav = directChild(shell, "nav[aria-label='Main navigation']");
        var main = directChild(shell, "main");
        if (!header || !nav || !main || shell.hasAttribute("data-app-shell")) return;
        shell.setAttribute("data-app-shell", "true");
        document.body.classList.add("app-shell-body");

        var routeLabels = {
            account: "Dashboard", admin: "Admin Dashboard", students: "Manage Students",
            history: "Moderation History", skills: "My Skills", projects: "Projects",
            teammates: "Find Teammates", "skill-gaps": "Skill Gap Analysis",
            "team-balance": "Team Balance Analyzer", "my-requests": "My Requests",
            invitations: "Invitations", "team-management": "Team Management",
            "my-teams": "My Teams", settings: "Settings", logout: "Sign Out"
        };
        var routeIcons = {
            account: ["M3 3h8v8H3z", "M13 3h8v5h-8z", "M13 10h8v11h-8z", "M3 13h8v8H3z"],
            skills: ["M12 3l2.2 5.8L20 11l-5.8 2.2L12 19l-2.2-5.8L4 11l5.8-2.2L12 3z", "M19 3v4", "M17 5h4"],
            projects: ["M3 7a2 2 0 012-2h5l2 2h7a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2z", "M3 10h18"],
            teammates: ["M16 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2", "M10 11a4 4 0 100-8 4 4 0 000 8z", "M20 21v-2a4 4 0 00-3-3.87", "M16 3.13a4 4 0 010 7.75"],
            "skill-gaps": ["M4 19V5", "M4 19h16", "M8 16v-5", "M12 16V8", "M16 16V4"],
            "team-balance": ["M12 3v18", "M3 12h18", "M5.6 5.6l12.8 12.8", "M18.4 5.6L5.6 18.4"],
            "my-requests": ["M8 6h13", "M8 12h13", "M8 18h13", "M3 6h.01", "M3 12h.01", "M3 18h.01"],
            invitations: ["M3 5h18v14H3z", "M3 6l9 7 9-7"],
            "team-management": ["M12 22s8-4 8-11V5l-8-3-8 3v6c0 7 8 11 8 11z", "M9 12l2 2 4-4"],
            "my-teams": ["M16 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2", "M10 11a4 4 0 100-8 4 4 0 000 8z", "M20 8v6", "M17 11h6"],
            settings: ["M12 8a4 4 0 100 8 4 4 0 000-8z", "M19.4 15a1.7 1.7 0 00.34 1.88l.06.06-1.7 2.94-.08-.02a1.7 1.7 0 00-1.7.5l-.06.06h-3.4l-.04-.08a1.7 1.7 0 00-1.53-.95h-.08L8.26 18l.02-.08a1.7 1.7 0 00-.5-1.7l-.06-.06v-3.4l.08-.04a1.7 1.7 0 00.95-1.53v-.08L10.45 8.2l.08.02a1.7 1.7 0 001.7-.5l.06-.06h3.4l.04.08a1.7 1.7 0 001.53.95h.08l2.94 1.7-.02.08a1.7 1.7 0 00.5 1.7l.06.06v3.4z"],
            logout: ["M10 17l5-5-5-5", "M15 12H3", "M12 3h6a2 2 0 012 2v14a2 2 0 01-2 2h-6"]
        };

        var dashboardLink = nav.querySelector("a[href$='/account']")
            || nav.querySelector("a[href$='/admin']") || nav.querySelector("a");
        var brand = nav.querySelector(".sidebar-brand");
        if (!brand) {
            // Keep a text fallback if a future JSP omits the shared image lockup.
            brand = document.createElement("a");
            brand.className = "sidebar-brand";
            var fallback = document.createElement("span");
            fallback.className = "sidebar-brand-label";
            fallback.textContent = "SkillSync";
            brand.appendChild(fallback);
            nav.insertBefore(brand, nav.firstChild);
        }
        brand.href = dashboardLink ? dashboardLink.href : "#";
        brand.setAttribute("aria-label", "SkillSync dashboard");

        var activeLink = nav.querySelector("a[aria-current='page']");
        if (!nav.id) nav.id = "main-sidebar";
        var activeLabel = "Student workspace";
        nav.querySelectorAll("a:not(.sidebar-brand)").forEach(function (link) {
            var route = new URL(link.href, window.location.href).pathname.replace(/\/$/, "").split("/").pop();
            var label = routeLabels[route] || link.textContent.trim();
            if (link.pathname.endsWith("/admin/projects")) label = "Manage Projects";
            if (link.pathname.endsWith("/admin/history")) label = "Moderation History";
            if (route === "team-management") label = "Team Management";
            if (route === "team-management") link.textContent = label;
            link.classList.add("sidebar-link");
            var iconSlot = document.createElement("span");
            iconSlot.className = "nav-icon";
            iconSlot.appendChild(icon(routeIcons[route] || routeIcons.projects));
            var text = document.createElement("span");
            text.className = "nav-label";
            text.textContent = label;
            link.textContent = "";
            link.appendChild(iconSlot);
            link.appendChild(text);
            link.title = label;
            if (link === activeLink) activeLabel = label;
        });

        var toggle = nav.querySelector("[data-theme-toggle]");
        if (toggle) toggle.parentNode.removeChild(toggle);
        var menuButton = document.createElement("button");
        menuButton.type = "button";
        menuButton.className = "sidebar-menu-toggle";
        menuButton.setAttribute("aria-label", "Toggle navigation sidebar");
        menuButton.setAttribute("aria-controls", nav.id);
        menuButton.setAttribute("aria-expanded", "true");
        menuButton.appendChild(icon(["M4 6h16", "M4 12h16", "M4 18h16"]));
        header.insertBefore(menuButton, header.firstChild);

        var oldWorkspace = directChild(header, ".topbar-context") || directChild(header, "span");
        if (oldWorkspace) {
            oldWorkspace.className = "topbar-context";
            oldWorkspace.textContent = activeLabel;
        }
        var oldBrand = header.querySelector(".topbar-title") || directChild(header, "strong, .brand");
        if (oldBrand && !oldBrand.classList.contains("topbar-title")) oldBrand.textContent = "SkillSync";

        var actions = document.createElement("div");
        actions.className = "topbar-actions";
        if (toggle) actions.appendChild(toggle);
        var profile = document.createElement("details");
        profile.className = "profile-menu";
        var summary = document.createElement("summary");
        summary.appendChild(icon(["M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2", "M12 11a4 4 0 100-8 4 4 0 000 8z"]));
        var summaryLabel = document.createElement("span");
        summaryLabel.textContent = "Account";
        summary.appendChild(summaryLabel);
        profile.appendChild(summary);
        if (dashboardLink && dashboardLink.pathname.endsWith("/account")) {
            var editProfile = document.createElement("a");
            editProfile.className = "profile-menu-link";
            editProfile.href = dashboardLink.href + "#profile-editor";
            editProfile.textContent = "Edit profile";
            profile.appendChild(editProfile);
        }
        ["/settings", "/logout"].forEach(function (suffix) {
            var source = nav.querySelector("a[href$='" + suffix + "']");
            if (source) {
                var item = source.cloneNode(true);
                item.className = "profile-menu-link";
                item.textContent = suffix === "/settings" ? "Settings" : "Sign Out";
                profile.appendChild(item);
            }
        });
        actions.appendChild(profile);
        var previousActions = directChild(header, ".topbar-actions");
        if (previousActions) header.removeChild(previousActions);
        header.appendChild(actions);

        // The same global search is mounted in every authenticated desktop/mobile top bar.
        var applicationPath = dashboardLink ? dashboardLink.pathname.replace(/\/(account|admin)\/?$/, "") : "";
        var search = document.createElement("div");
        search.className = "global-search";
        search.setAttribute("data-admin-search", String(!!nav.querySelector("a[href$='/admin']")));
        var searchForm = document.createElement("form");
        searchForm.className = "global-search-form";
        searchForm.setAttribute("role", "search");
        searchForm.setAttribute("action", applicationPath + "/search");
        searchForm.setAttribute("method", "get");
        var searchLabel = document.createElement("label");
        searchLabel.className = "visually-hidden";
        searchLabel.setAttribute("for", "global-search-input");
        searchLabel.textContent = "Search projects and student profiles";
        var searchIcon = icon(["M20 20l-4.3-4.3", "M10.8 17a6.2 6.2 0 110-12.4 6.2 6.2 0 010 12.4z"]);
        searchIcon.classList.add("global-search-icon");
        var searchInput = document.createElement("input");
        searchInput.id = "global-search-input";
        searchInput.name = "q";
        searchInput.type = "search";
        searchInput.placeholder = "Search projects and people";
        searchInput.autocomplete = "off";
        searchInput.setAttribute("role", "combobox");
        searchInput.setAttribute("aria-autocomplete", "list");
        searchInput.setAttribute("aria-controls", "global-search-results");
        searchInput.setAttribute("aria-expanded", "false");
        searchForm.appendChild(searchLabel);
        searchForm.appendChild(searchIcon);
        searchForm.appendChild(searchInput);
        var searchResults = document.createElement("div");
        searchResults.id = "global-search-results";
        searchResults.className = "global-search-results";
        searchResults.setAttribute("role", "listbox");
        searchResults.setAttribute("aria-label", "Search results");
        searchResults.hidden = true;
        search.appendChild(searchForm);
        search.appendChild(searchResults);
        header.insertBefore(search, actions);

        var backdrop = document.createElement("button");
        backdrop.type = "button";
        backdrop.className = "sidebar-backdrop";
        backdrop.setAttribute("aria-label", "Close navigation");
        document.body.appendChild(backdrop);

        function isMobile() { return window.matchMedia("(max-width: 800px)").matches; }
        menuButton.setAttribute("aria-expanded", String(!isMobile()));
        function closeMobile() {
            var wasOpen = document.body.classList.contains("mobile-nav-open");
            document.body.classList.remove("mobile-nav-open");
            menuButton.setAttribute("aria-expanded", "false");
            if (wasOpen) menuButton.focus();
        }
        menuButton.addEventListener("click", function () {
            if (isMobile()) {
                var open = document.body.classList.toggle("mobile-nav-open");
                menuButton.setAttribute("aria-expanded", String(open));
                if (open) {
                    var firstLink = nav.querySelector("a.sidebar-link");
                    if (firstLink) firstLink.focus();
                }
            } else {
                var collapsed = document.body.classList.toggle("sidebar-collapsed");
                menuButton.setAttribute("aria-expanded", String(!collapsed));
                try { window.localStorage.setItem("smartProjectMatcherSidebarCollapsed", String(collapsed)); } catch (ignored) { }
            }
        });
        backdrop.addEventListener("click", closeMobile);
        nav.querySelectorAll("a:not(.sidebar-brand)").forEach(function (link) {
            link.addEventListener("click", function () { if (isMobile()) closeMobile(); });
        });
        document.addEventListener("keydown", function (event) {
            if (event.key === "Escape") {
                closeMobile();
                profile.open = false;
            }
        });
        try {
            if (!isMobile() && window.localStorage.getItem("smartProjectMatcherSidebarCollapsed") === "true") {
                document.body.classList.add("sidebar-collapsed");
                menuButton.setAttribute("aria-expanded", "false");
            }
        } catch (ignored) { }
    }

    function savePreferences() {
        try {
            window.localStorage.setItem(themeKey, theme);
            window.localStorage.setItem(fontKey, font);
            return true;
        } catch (ignored) {
            return false;
        }
    }

    function initializeGlobalSearch() {
        var input = document.getElementById("global-search-input");
        var results = document.getElementById("global-search-results");
        if (!input || !results) return;
        var form = input.form;
        var timer = 0;
        var requestController = null;
        var requestNumber = 0;
        var endpoint = form.getAttribute("action");

        function hideResults() {
            results.hidden = true;
            input.setAttribute("aria-expanded", "false");
        }

        function showResults() {
            results.hidden = false;
            input.setAttribute("aria-expanded", "true");
        }

        function appendSection(title, entries, type) {
            if (!entries || entries.length === 0) return;
            var heading = document.createElement("div");
            heading.className = "global-search-heading";
            heading.textContent = title;
            results.appendChild(heading);
            entries.forEach(function (entry) {
                if (type === "project") {
                    var link = document.createElement("a");
                    link.className = "global-search-result global-search-project";
                    link.setAttribute("role", "option");
                    link.setAttribute("data-search-link", "true");
                    var destination = search.getAttribute("data-admin-search") === "true" ? "/admin/projects" : "/projects";
                    link.href = endpoint.replace(/\/search\/?$/, destination) + "#project-" + encodeURIComponent(entry.id);
                    var titleText = document.createElement("strong");
                    titleText.textContent = entry.title || "Untitled project";
                    var meta = document.createElement("span");
                    meta.textContent = (entry.status || "Project").replace(/_/g, " ") + " · by " + (entry.creator || "Student");
                    var skills = document.createElement("small");
                    skills.textContent = entry.skills ? "Skills: " + entry.skills : "No skills listed";
                    link.appendChild(titleText);
                    link.appendChild(meta);
                    link.appendChild(skills);
                    results.appendChild(link);
                } else {
                    var card = document.createElement("div");
                    card.className = "global-search-result global-search-student";
                    card.setAttribute("role", "option");
                    var name = document.createElement("strong");
                    name.textContent = entry.name || "Student profile";
                    var meta = document.createElement("span");
                    var availabilityLabels = { AVAILABLE: "Available", LIMITED: "Limited availability", UNAVAILABLE: "Unavailable" };
                    var availability = availabilityLabels[entry.availability] || "Availability not set";
                    meta.textContent = (entry.department || "Department not specified") + " · Year " + entry.year + " · " + availability;
                    card.appendChild(name);
                    card.appendChild(meta);
                    if (entry.bio) {
                        var bio = document.createElement("small");
                        bio.textContent = entry.bio;
                        card.appendChild(bio);
                    }
                    if (entry.skills) {
                        var skills = document.createElement("small");
                        skills.textContent = "Skills: " + entry.skills;
                        card.appendChild(skills);
                    }
                    if (entry.interests) {
                        var interests = document.createElement("small");
                        interests.textContent = "Interests: " + entry.interests;
                        card.appendChild(interests);
                    }
                    results.appendChild(card);
                }
            });
        }

        function showEmpty(text) {
            var message = document.createElement("p");
            message.className = "global-search-empty";
            message.textContent = text;
            results.appendChild(message);
            showResults();
        }

        function render(data) {
            results.textContent = "";
            appendSection("Projects", data.projects, "project");
            appendSection("Student profiles", data.students, "student");
            if (!results.children.length) {
                showEmpty("No projects or student profiles matched your search.");
            } else {
                showResults();
            }
            results.removeAttribute("aria-busy");
        }

        function showLoading() {
            results.textContent = "";
            results.setAttribute("aria-busy", "true");
            var loading = document.createElement("div");
            loading.className = "search-skeleton-list";
            loading.setAttribute("aria-label", "Loading search results");
            for (var index = 0; index < 3; index++) {
                var item = document.createElement("span");
                item.className = "search-skeleton";
                item.setAttribute("aria-hidden", "true");
                loading.appendChild(item);
            }
            results.appendChild(loading);
            showResults();
        }

        function searchNow(query) {
            if (requestController) requestController.abort();
            requestController = new AbortController();
            var currentRequest = ++requestNumber;
            showLoading();
            fetch(endpoint + "?q=" + encodeURIComponent(query), {
                method: "GET",
                credentials: "same-origin",
                headers: { "Accept": "application/json" },
                signal: requestController.signal
            }).then(function (response) {
                if (!response.ok) throw new Error("Search unavailable");
                return response.json();
            }).then(function (data) {
                if (currentRequest === requestNumber) render(data);
            }).catch(function (error) {
                if (error.name === "AbortError" || currentRequest !== requestNumber) return;
                results.textContent = "";
                results.removeAttribute("aria-busy");
                showEmpty("Search is temporarily unavailable. Please try again.");
            });
        }

        form.addEventListener("submit", function (event) {
            event.preventDefault();
            var query = input.value.trim();
            if (query.length >= 2) searchNow(query);
        });
        input.addEventListener("input", function () {
            window.clearTimeout(timer);
            var query = input.value.trim();
            if (requestController) requestController.abort();
            if (query.length < 2) {
                results.textContent = "";
                results.removeAttribute("aria-busy");
                hideResults();
                return;
            }
            // The previous request is stale; clear its skeleton until the debounced fetch starts.
            results.textContent = "";
            results.removeAttribute("aria-busy");
            hideResults();
            timer = window.setTimeout(function () { searchNow(query); }, 240);
        });
        input.addEventListener("keydown", function (event) {
            if (event.key === "Escape") {
                hideResults();
            } else if (event.key === "ArrowDown") {
                var firstResult = results.querySelector("[data-search-link]");
                if (firstResult) {
                    event.preventDefault();
                    firstResult.focus();
                }
            }
        });
        results.addEventListener("click", function (event) {
            if (event.target.closest("[data-search-link]")) hideResults();
        });
        document.addEventListener("click", function (event) {
            if (!search.contains(event.target)) hideResults();
        });
    }

    function initializeToasts() {
        var notices = document.querySelectorAll("p.message, p.success, p.error");
        if (!notices.length) return;
        var region = document.createElement("div");
        region.className = "toast-region";
        region.setAttribute("aria-label", "Page notifications");
        region.setAttribute("aria-live", "polite");
        region.setAttribute("aria-relevant", "additions");
        document.body.appendChild(region);
        notices.forEach(function (notice, index) {
            notice.classList.add("toast-notice");
            notice.setAttribute("data-toast", "true");
            var close = document.createElement("button");
            close.type = "button";
            close.className = "toast-close";
            close.setAttribute("aria-label", "Dismiss notification");
            close.textContent = "×";
            close.addEventListener("click", function () { notice.remove(); });
            notice.appendChild(close);
            region.appendChild(notice);
            if (!notice.classList.contains("error") && notice.getAttribute("role") !== "alert") {
                window.setTimeout(function () {
                    notice.classList.add("toast-leaving");
                    window.setTimeout(function () { notice.remove(); }, 220);
                }, 7000 + index * 250);
            }
        });
    }

    document.addEventListener("DOMContentLoaded", function () {
        mountAppShell();
        initializeGlobalSearch();
        initializeToasts();
        syncLogoAssets();
        syncControls();
        document.querySelectorAll("[data-theme-choice]").forEach(function (control) {
            control.addEventListener("change", function () {
                if (control.checked) applyTheme(control.value);
                syncControls();
            });
        });
        document.querySelectorAll("[data-font-choice]").forEach(function (control) {
            control.addEventListener("change", function () {
                if (control.checked) applyFont(control.value);
                syncControls();
            });
        });
        document.querySelectorAll("[data-theme-toggle]").forEach(function (button) {
            button.addEventListener("click", function () {
                applyTheme(theme === "light" ? "midnight-purple" : "light");
                savePreferences();
                syncControls();
            });
        });
        document.querySelectorAll("[data-save-preferences]").forEach(function (button) {
            button.addEventListener("click", function () {
                var status = document.querySelector("[data-preferences-status]");
                if (status) {
                    status.textContent = savePreferences()
                        ? "Your appearance preferences have been saved."
                        : "Browser storage is unavailable; these choices will last only until you leave this page.";
                    status.setAttribute("role", "status");
                }
            });
        });
        document.querySelectorAll("[data-reset-preferences]").forEach(function (button) {
            button.addEventListener("click", function () {
                applyTheme("midnight-purple");
                applyFont("Inter");
                var saved = savePreferences();
                syncControls();
                var status = document.querySelector("[data-preferences-status]");
                if (status) {
                    status.textContent = saved
                        ? "Default preferences restored: Midnight Purple and Inter."
                        : "Defaults are active for this page, but browser storage is unavailable.";
                    status.setAttribute("role", "status");
                }
            });
        });
        document.querySelectorAll("[data-password-toggle]").forEach(function (button) {
            button.addEventListener("click", function () {
                var wrapper = button.closest(".password-input");
                var input = wrapper && wrapper.querySelector("input[type='password'], input[type='text']");
                if (!input) return;
                var visible = input.type === "password";
                input.type = visible ? "text" : "password";
                button.textContent = visible ? "Hide" : "Show";
                button.setAttribute("aria-label", visible ? "Hide password" : "Show password");
                button.setAttribute("aria-pressed", String(visible));
            });
        });
    });
}());
