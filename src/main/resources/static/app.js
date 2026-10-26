// UptimeWatch status page: renders /api/monitors and /api/incidents, refreshes every 30 s.
const REFRESH_MS = 30000;
const RECENT_SLOTS = 30;
const KEY_STORAGE = "uptimewatch.apiKey";
const THEME_STORAGE = "uptimewatch.theme";

const state = {
    monitors: [],
    incidents: [],
    filter: "all",
    search: "",
    category: "",
    collapsed: new Set(),
    openMonitorId: null,
};

const $ = (id) => document.getElementById(id);

// ---------- Small helpers ----------

const escapeHtml = (value) => String(value ?? "").replace(/[&<>"']/g, (c) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
}[c]));

function storageGet(store, key) {
    try { return store.getItem(key); } catch { return null; }
}

function storageSet(store, key, value) {
    try { value == null ? store.removeItem(key) : store.setItem(key, value); } catch { /* storage unavailable */ }
}

function humanDuration(seconds) {
    seconds = Math.max(0, Math.round(seconds));
    if (seconds < 60) return `${seconds}s`;
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) return `${minutes} min`;
    const hours = Math.floor(minutes / 60);
    if (hours < 48) return `${hours} h ${minutes % 60} min`;
    return `${Math.floor(hours / 24)} days`;
}

const since = (iso) => (iso ? humanDuration((Date.now() - new Date(iso)) / 1000) : null);
const pct = (value) => (value == null ? "–" : `${value.toFixed(2)}%`);
const ms = (value) => (value == null ? "–" : `${value.toLocaleString()} ms`);
const formatTime = (iso) => new Date(iso).toLocaleString([], {
    day: "numeric", month: "short", hour: "2-digit", minute: "2-digit"
});

function toast(message) {
    const el = $("toast");
    el.textContent = message;
    el.hidden = false;
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => { el.hidden = true; }, 3200);
}

async function getJson(url) {
    const response = await fetch(url, { headers: { Accept: "application/json" } });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return response.json();
}

// ---------- Theme ----------

function applyTheme(theme) {
    if (theme) document.documentElement.dataset.theme = theme;
    else delete document.documentElement.dataset.theme;
}

applyTheme(storageGet(localStorage, THEME_STORAGE));

$("themeToggle").addEventListener("click", () => {
    const current = document.documentElement.dataset.theme
        || (matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light");
    const next = current === "dark" ? "light" : "dark";
    applyTheme(next);
    storageSet(localStorage, THEME_STORAGE, next);
});

// ---------- Summary ----------

function renderHero() {
    const hero = $("hero");
    const down = state.monitors.filter((m) => m.status === "DOWN");
    const checked = state.monitors.filter((m) => m.status !== "UNKNOWN");
    let cls = "hero-unknown", icon = "…", title = "Waiting for the first checks…";
    let sub = "Live status of popular Indian and global services, checked every minute.";
    if (checked.length > 0 && down.length === 0) {
        cls = "hero-up"; icon = "✓"; title = "All systems operational";
        sub = `${checked.length} services are up and responding normally.`;
    } else if (down.length > 0) {
        cls = "hero-down"; icon = "!";
        title = down.length === 1 ? `${down[0].name} is down` : `${down.length} services are down`;
        sub = down.slice(0, 3).map((m) => `${m.name} (for ${since(m.statusSince)})`).join(" · ")
            + (down.length > 3 ? ` and ${down.length - 3} more` : "");
    }
    hero.className = `hero ${cls}`;
    $("heroIcon").textContent = icon;
    $("heroTitle").textContent = title;
    $("heroSub").textContent = sub;
}

function renderStats() {
    const ms24 = state.monitors.map((m) => m.avgResponseMs24h).filter((v) => v != null);
    const up24 = state.monitors.map((m) => m.uptime24h).filter((v) => v != null);
    const avg = (list) => list.reduce((a, b) => a + b, 0) / list.length;
    $("statTotal").textContent = state.monitors.length;
    $("statUp").textContent = state.monitors.filter((m) => m.status === "UP").length;
    $("statDown").textContent = state.monitors.filter((m) => m.status === "DOWN").length;
    $("statAvg").textContent = ms24.length ? `${Math.round(avg(ms24))} ms` : "–";
    $("statUptime").textContent = up24.length ? pct(avg(up24)) : "–";
}

// ---------- Service list ----------

function renderBars(checks) {
    const padding = Array(Math.max(0, RECENT_SLOTS - checks.length)).fill('<span class="tick"></span>');
    const bars = checks.map((c) => {
        const title = `${formatTime(c.checkedAt)} · ${c.up ? "up" : "down"}`
            + (c.responseTimeMs != null ? ` · ${c.responseTimeMs} ms` : "")
            + (c.error ? ` · ${c.error}` : "");
        return `<span class="tick ${c.up ? "tick-up" : "tick-down"}" title="${escapeHtml(title)}"></span>`;
    });
    return padding.concat(bars).join("");
}

function sslBadge(days) {
    if (days == null) return "";
    if (days < 0) return '<span class="badge badge-bad">SSL expired</span>';
    if (days < 14) return `<span class="badge badge-warn">SSL ${days}d</span>`;
    return "";
}

function stateText(m) {
    if (m.status === "UP") return `Up · ${since(m.statusSince)}`;
    if (m.status === "DOWN") return `Down · ${since(m.statusSince)}`;
    return m.consecutiveFailures > 0 ? "Retrying…" : "Pending";
}

function renderMonitor(m) {
    return `
    <li class="monitor" tabindex="0" role="button" data-id="${m.id}" aria-label="${escapeHtml(m.name)}, ${m.status.toLowerCase()}. Open details">
        <div style="min-width:0">
            <div class="name"><span class="dot dot-${m.status}"></span><span>${escapeHtml(m.name)}</span></div>
            <div class="url">${escapeHtml(m.url.replace(/^https?:\/\//, ""))}</div>
        </div>
        <div class="bars" aria-hidden="true">${renderBars(m.recentChecks)}</div>
        <div class="meta">
            <div class="state state-${m.status}">${stateText(m)} ${sslBadge(m.sslDaysLeft)}</div>
            <div class="sub">${pct(m.uptime24h)} · ${ms(m.lastResponseTimeMs)}</div>
        </div>
    </li>`;
}

function matches(m) {
    if (state.filter !== "all" && m.status !== state.filter) return false;
    if (state.category && m.category !== state.category) return false;
    if (state.search) {
        const q = state.search.toLowerCase();
        return m.name.toLowerCase().includes(q) || m.url.toLowerCase().includes(q);
    }
    return true;
}

function renderGroups() {
    const visible = state.monitors.filter(matches);
    const groups = new Map();
    for (const m of visible) {
        if (!groups.has(m.category)) groups.set(m.category, []);
        groups.get(m.category).push(m);
    }
    // Groups with problems first, then alphabetical.
    const ordered = [...groups.entries()].sort(([a, la], [b, lb]) => {
        const downA = la.some((m) => m.status === "DOWN");
        const downB = lb.some((m) => m.status === "DOWN");
        if (downA !== downB) return downA ? -1 : 1;
        return a.localeCompare(b);
    });

    $("groups").innerHTML = ordered.map(([category, list]) => {
        const down = list.filter((m) => m.status === "DOWN").length;
        const count = down ? `${down} of ${list.length} down` : `${list.length} up`;
        const sorted = [...list].sort((a, b) =>
            (a.status === "DOWN") === (b.status === "DOWN") ? a.name.localeCompare(b.name) : a.status === "DOWN" ? -1 : 1);
        return `
        <details class="group" data-category="${escapeHtml(category)}" ${state.collapsed.has(category) ? "" : "open"}>
            <summary>${escapeHtml(category)}<span class="group-count ${down ? "bad" : ""}">${count}</span></summary>
            <ul class="monitors">${sorted.map(renderMonitor).join("")}</ul>
        </details>`;
    }).join("");
    $("emptyState").hidden = visible.length > 0;
}

function renderCategoryOptions() {
    const categories = [...new Set(state.monitors.map((m) => m.category))].sort();
    const select = $("categoryFilter");
    const current = select.value;
    select.innerHTML = '<option value="">All categories</option>'
        + categories.map((c) => `<option ${c === current ? "selected" : ""}>${escapeHtml(c)}</option>`).join("");
    $("categoryOptions").innerHTML = categories.map((c) => `<option value="${escapeHtml(c)}">`).join("");
}

// ---------- Incidents ----------

function renderIncident(i) {
    const when = i.ongoing
        ? `Ongoing · started ${formatTime(i.startedAt)} (${humanDuration(i.durationSeconds)})`
        : `${formatTime(i.startedAt)} · lasted ${humanDuration(i.durationSeconds)}`;
    return `
    <li class="incident ${i.ongoing ? "ongoing" : ""}">
        <span class="incident-marker" aria-hidden="true"></span>
        <div>
            <div class="incident-title">${escapeHtml(i.monitorName)}
                ${i.ongoing ? '<span class="badge badge-bad">Ongoing</span>' : '<span class="badge badge-ok">Resolved</span>'}</div>
            <div class="small muted">${when}</div>
            ${i.cause ? `<div class="incident-cause">${escapeHtml(i.cause)}</div>` : ""}
        </div>
    </li>`;
}

function renderIncidents() {
    $("incidentList").innerHTML = state.incidents.length
        ? state.incidents.map(renderIncident).join("")
        : '<li class="empty">No incidents recorded yet. Everything has been running smoothly.</li>';
}

// ---------- Detail dialog ----------

function renderChart(checks) {
    // checks arrive newest first; draw oldest to newest, left to right.
    const points = [...checks].reverse();
    if (points.length < 2) return '<p class="muted small">Not enough data yet. Check back in a few minutes.</p>';
    const W = 700, H = 170, P = { l: 44, r: 8, t: 10, b: 22 };
    const values = points.map((c) => c.responseTimeMs ?? 0);
    const max = Math.max(100, ...values) * 1.1;
    const x = (i) => P.l + (i / (points.length - 1)) * (W - P.l - P.r);
    const y = (v) => H - P.b - (v / max) * (H - P.t - P.b);
    const line = points.map((c, i) => `${i ? "L" : "M"}${x(i).toFixed(1)},${y(c.up ? c.responseTimeMs : 0).toFixed(1)}`).join(" ");
    const area = `${line} L${x(points.length - 1).toFixed(1)},${H - P.b} L${P.l},${H - P.b} Z`;
    const ticks = [0, 0.5, 1].map((f) => Math.round(max * f));
    const grid = ticks.map((t) => `<line class="grid" x1="${P.l}" x2="${W - P.r}" y1="${y(t)}" y2="${y(t)}"/>
        <text class="axis" x="${P.l - 6}" y="${y(t) + 3}" text-anchor="end">${t} ms</text>`).join("");
    const fails = points.map((c, i) => c.up ? "" : `<circle class="fail" cx="${x(i)}" cy="${H - P.b}" r="3.5"><title>${escapeHtml(formatTime(c.checkedAt) + " · " + (c.error || "down"))}</title></circle>`).join("");
    return `<svg class="chart" viewBox="0 0 ${W} ${H}" preserveAspectRatio="none" role="img"
                aria-label="Response time for the last ${points.length} checks">
        ${grid}<path class="area" d="${area}"/><path class="line" d="${line}"/>${fails}
        <text class="axis" x="${P.l}" y="${H - 6}">${formatTime(points[0].checkedAt)}</text>
        <text class="axis" x="${W - P.r}" y="${H - 6}" text-anchor="end">now</text>
    </svg>`;
}

async function openDetail(id) {
    const m = state.monitors.find((x) => x.id === id);
    if (!m) return;
    state.openMonitorId = id;
    $("detailTitle").textContent = m.name;
    $("detailUrl").textContent = m.url;
    $("detailUrl").href = m.url;
    $("detailBody").innerHTML = '<p class="muted">Loading…</p>';
    document.querySelector("#detailDialog .admin-only").hidden = !storageGet(sessionStorage, KEY_STORAGE);
    resetDeleteButton();
    $("detailDialog").showModal();

    try {
        const [checks, incidents] = await Promise.all([
            getJson(`/api/monitors/${id}/checks?limit=120`),
            getJson(`/api/monitors/${id}/incidents?limit=5`),
        ]);
        const ssl = m.sslDaysLeft == null ? "–" : m.sslDaysLeft < 0 ? "Expired" : `${m.sslDaysLeft} days`;
        $("detailBody").innerHTML = `
            <div class="detail-stats">
                <div class="stat"><span class="stat-label">Status</span><span class="stat-value state-${m.status}">${m.status}</span></div>
                <div class="stat"><span class="stat-label">Uptime 24h / 7d</span><span class="stat-value">${pct(m.uptime24h)}</span><span class="small muted">7d: ${pct(m.uptime7d)}</span></div>
                <div class="stat"><span class="stat-label">Avg response (24h)</span><span class="stat-value">${ms(m.avgResponseMs24h)}</span></div>
                <div class="stat"><span class="stat-label">SSL certificate</span><span class="stat-value">${ssl}</span></div>
            </div>
            ${m.status === "DOWN" && m.lastError ? `<div class="error-box"><b>Last error:</b> ${escapeHtml(m.lastError)}</div>` : ""}
            <div class="detail-section">
                <h3>Response time · last ${checks.length} check${checks.length === 1 ? "" : "s"}</h3>
                ${renderChart(checks)}
                <p class="small muted">Last checked ${m.lastCheckedAt ? since(m.lastCheckedAt) + " ago" : "never"}
                    · status code ${m.lastStatusCode ?? "none"} · checked every ${m.intervalSeconds} s
                    · category ${escapeHtml(m.category)}</p>
            </div>
            <div class="detail-section">
                <h3>Incidents</h3>
                <ul class="incidents">${incidents.length ? incidents.map(renderIncident).join("")
                    : '<li class="empty">No incidents for this service.</li>'}</ul>
            </div>`;
    } catch (e) {
        $("detailBody").innerHTML = `<p class="error-box">Could not load details (${escapeHtml(e.message)}).</p>`;
    }
}

function resetDeleteButton() {
    const btn = $("deleteBtn");
    btn.textContent = "Remove this site";
    btn.dataset.armed = "";
}

$("deleteBtn").addEventListener("click", async () => {
    const btn = $("deleteBtn");
    if (!btn.dataset.armed) {
        btn.dataset.armed = "1";
        btn.textContent = "Click again to confirm";
        return;
    }
    const key = storageGet(sessionStorage, KEY_STORAGE);
    const response = await fetch(`/api/monitors/${state.openMonitorId}`, {
        method: "DELETE", headers: { "X-Api-Key": key || "" },
    });
    if (response.status === 204) {
        $("detailDialog").close();
        toast("Site removed");
        refresh();
    } else {
        if (response.status === 401) storageSet(sessionStorage, KEY_STORAGE, null);
        toast(response.status === 401 ? "Admin key no longer valid" : `Could not remove (HTTP ${response.status})`);
        resetDeleteButton();
    }
});

// ---------- Add site ----------

$("addSiteBtn").addEventListener("click", () => {
    const form = $("addForm");
    form.reset();
    form.apiKey.value = storageGet(sessionStorage, KEY_STORAGE) || "";
    $("addError").hidden = true;
    $("addDialog").showModal();
    form.name.focus();
});

$("addForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.target;
    const error = $("addError");
    error.hidden = true;

    let url = form.url.value.trim();
    if (url && !/^https?:\/\//i.test(url)) url = `https://${url}`;
    if (!form.name.value.trim() || !url || !form.apiKey.value) {
        error.textContent = "Please fill in the name, URL and admin key.";
        error.hidden = false;
        return;
    }

    const submit = $("addSubmit");
    submit.disabled = true;
    try {
        const response = await fetch("/api/monitors", {
            method: "POST",
            headers: { "Content-Type": "application/json", "X-Api-Key": form.apiKey.value },
            body: JSON.stringify({
                name: form.name.value.trim(),
                url,
                category: form.category.value.trim() || null,
                intervalSeconds: Number(form.intervalSeconds.value),
            }),
        });
        if (response.status === 201) {
            storageSet(sessionStorage, KEY_STORAGE, form.apiKey.value);
            $("addDialog").close();
            toast("Site added. The first check runs within 10 seconds.");
            refresh();
            return;
        }
        if (response.status === 401) {
            error.textContent = "That admin key is not correct.";
        } else if (response.status === 400) {
            const body = await response.json();
            error.textContent = Object.entries(body.fields || {}).map(([f, m]) => `${f}: ${m}`).join(" · ")
                || "Please check the form.";
        } else {
            error.textContent = `Something went wrong (HTTP ${response.status}).`;
        }
        error.hidden = false;
    } catch (e) {
        error.textContent = `Could not reach the server (${e.message}).`;
        error.hidden = false;
    } finally {
        submit.disabled = false;
    }
});

// ---------- Wiring ----------

document.querySelectorAll("[data-close]").forEach((btn) =>
    btn.addEventListener("click", () => btn.closest("dialog").close()));

document.querySelectorAll("dialog").forEach((dialog) =>
    dialog.addEventListener("click", (e) => { if (e.target === dialog) dialog.close(); }));

$("groups").addEventListener("click", (e) => {
    const row = e.target.closest(".monitor");
    if (row) openDetail(Number(row.dataset.id));
});

$("groups").addEventListener("keydown", (e) => {
    const row = e.target.closest(".monitor");
    if (row && (e.key === "Enter" || e.key === " ")) {
        e.preventDefault();
        openDetail(Number(row.dataset.id));
    }
});

$("groups").addEventListener("toggle", (e) => {
    const group = e.target.closest(".group");
    if (!group) return;
    if (group.open) state.collapsed.delete(group.dataset.category);
    else state.collapsed.add(group.dataset.category);
}, true);

$("search").addEventListener("input", (e) => { state.search = e.target.value.trim(); renderGroups(); });
$("categoryFilter").addEventListener("change", (e) => { state.category = e.target.value; renderGroups(); });

document.querySelectorAll(".chip").forEach((chip) => chip.addEventListener("click", () => {
    document.querySelectorAll(".chip").forEach((c) => c.classList.toggle("active", c === chip));
    state.filter = chip.dataset.filter;
    renderGroups();
}));

async function refresh() {
    try {
        const [monitors, incidents] = await Promise.all([getJson("/api/monitors"), getJson("/api/incidents?limit=15")]);
        state.monitors = monitors;
        state.incidents = incidents;
        renderHero();
        renderStats();
        renderCategoryOptions();
        renderGroups();
        renderIncidents();
        $("updated").textContent = `Updated ${new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" })}`;
    } catch (e) {
        $("updated").textContent = `Could not reach the server (${e.message}). Retrying…`;
    }
}

refresh();
setInterval(refresh, REFRESH_MS);
