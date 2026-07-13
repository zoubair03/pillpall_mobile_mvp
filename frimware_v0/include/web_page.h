#pragma once
// ============================================================
//  PillPal — web_page.h
//  Full single-page app stored in flash (no SPIFFS needed).
//  Served by ESPAsyncWebServer on GET /
//
//  Sections:
//    - Header: PillPal logo + connection status
//    - Clock card: live time + set time/date
//    - Schedule card: set HH:MM for each dose
//    - Dispense card: manual dispense buttons per motor
//    - Status card: battery, WiFi, motor states, missed doses
//
//  REST API calls (all JSON):
//    GET  /api/status     → system snapshot
//    POST /api/time       → { "hh":8,"mm":0,"ss":0,"dd":1,"mo":1,"yr":2024 }
//    POST /api/schedule   → { "morning":"08:00","midday":"13:00","night":"21:00" }
//    POST /api/dispense   → { "dose":"morning"|"midday"|"night" }
//    POST /api/ack        → { "dose":"morning"|"midday"|"night"|"all" }
//    POST /api/home       → { "motor":"morning"|"midday"|"night"|"all" }
// ============================================================

static const char PILLPAL_HTML[] PROGMEM = R"rawhtml(
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>PillPal</title>
<style>
  :root {
    --bg: #0f1117;
    --surface: #1a1d27;
    --surface2: #22263a;
    --accent: #4f8ef7;
    --accent2: #7c3aed;
    --green: #22c55e;
    --red: #ef4444;
    --yellow: #f59e0b;
    --text: #e2e8f0;
    --muted: #64748b;
    --radius: 14px;
    --shadow: 0 4px 24px rgba(0,0,0,0.4);
    --navy: #0f172a;
    --teal: #0d9488;
  }
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    font-family: 'Segoe UI', system-ui, sans-serif;
    background: var(--bg);
    color: var(--text);
    min-height: 100vh;
    padding: 16px;
  }
  h1 {
    font-size: 1.5rem;
    font-weight: 700;
    letter-spacing: 0.025em;
    color: white;
  }
  header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 24px;
    padding: 16px 20px;
    background: var(--navy);
    border-radius: var(--radius);
    box-shadow: var(--shadow);
  }
  .header-title {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .header-title svg {
    width: 32px;
    height: 32px;
    color: var(--teal);
  }
  .badge {
    font-size: 0.875rem;
    padding: 4px 12px;
    border-radius: 99px;
    font-weight: 500;
    color: white;
  }
  .badge-green { background: var(--teal); }
  .badge-yellow { background: var(--yellow); }
  .badge-red { background: var(--red); }
  .grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
    gap: 16px;
  }
  .card {
    background: var(--surface);
    border-radius: var(--radius);
    padding: 20px;
    box-shadow: var(--shadow);
    border: 1px solid rgba(255,255,255,0.05);
  }
  .card h2 {
    font-size: 0.85rem;
    text-transform: uppercase;
    letter-spacing: 0.08em;
    color: var(--muted);
    margin-bottom: 16px;
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .card h2 .icon { font-size: 1rem; }
  .clock-display {
    font-size: 3rem;
    font-weight: 700;
    text-align: center;
    letter-spacing: 0.04em;
    background: linear-gradient(135deg, var(--accent), var(--accent2));
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
    margin-bottom: 4px;
  }
  .date-display {
    text-align: center;
    color: var(--muted);
    font-size: 0.9rem;
    margin-bottom: 16px;
  }
  .form-row {
    display: flex;
    gap: 8px;
    align-items: center;
    margin-bottom: 10px;
    flex-wrap: wrap;
  }
  label {
    font-size: 0.8rem;
    color: var(--muted);
    min-width: 70px;
  }
  input[type="time"],
  input[type="date"],
  input[type="text"],
  input[type="number"],
  input[type="password"] {
    background: var(--surface2);
    border: 1px solid rgba(255,255,255,0.1);
    color: var(--text);
    border-radius: 8px;
    padding: 8px 12px;
    font-size: 0.9rem;
    flex: 1;
    min-width: 120px;
    outline: none;
    transition: border 0.2s;
  }
  input:focus { border-color: var(--accent); }
  button {
    background: linear-gradient(135deg, var(--accent), var(--accent2));
    color: #fff;
    border: none;
    border-radius: 8px;
    padding: 9px 18px;
    font-size: 0.85rem;
    font-weight: 600;
    cursor: pointer;
    transition: opacity 0.2s, transform 0.1s;
    white-space: nowrap;
  }
  button:hover { opacity: 0.88; transform: translateY(-1px); }
  button:active { transform: translateY(0); }
  button.danger {
    background: linear-gradient(135deg, var(--red), #b91c1c);
  }
  button.success {
    background: linear-gradient(135deg, var(--green), #15803d);
  }
  button.warn {
    background: linear-gradient(135deg, var(--yellow), #b45309);
  }
  button.ghost {
    background: transparent;
    border: 1px solid rgba(255,255,255,0.15);
    color: var(--text);
  }
  .dose-row {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px;
    background: var(--surface2);
    border-radius: 10px;
    margin-bottom: 10px;
  }
  .dose-label {
    font-weight: 600;
    font-size: 0.9rem;
    min-width: 70px;
  }
  .dose-time {
    font-size: 1.2rem;
    font-weight: 700;
    color: var(--accent);
    flex: 1;
  }
  .dispense-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 10px;
  }
  .dispense-card {
    background: var(--surface2);
    border-radius: 10px;
    padding: 14px 10px;
    text-align: center;
  }
  .dispense-card .motor-name {
    font-size: 0.8rem;
    color: var(--muted);
    margin-bottom: 6px;
  }
  .dispense-card .slot-num {
    font-size: 1.5rem;
    font-weight: 700;
    color: var(--accent);
    margin-bottom: 8px;
  }
  .dispense-card .motor-state {
    font-size: 0.7rem;
    margin-bottom: 10px;
    font-weight: 600;
  }
  .state-homed     { color: var(--green); }
  .state-homing    { color: var(--yellow); }
  .state-dispensing{ color: var(--accent); }
  .state-fault     { color: var(--red); }
  .state-idle      { color: var(--muted); }
  .status-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 0;
    border-bottom: 1px solid rgba(255,255,255,0.05);
    font-size: 0.88rem;
  }
  .status-row:last-child { border-bottom: none; }
  .status-key { color: var(--muted); }
  .status-val { font-weight: 600; }
  .battery-bar {
    height: 6px;
    background: var(--surface2);
    border-radius: 99px;
    overflow: hidden;
    margin-top: 4px;
  }
  .battery-fill {
    height: 100%;
    border-radius: 99px;
    background: linear-gradient(90deg, var(--green), var(--accent));
    transition: width 0.5s;
  }
  .alert-banner {
    background: rgba(239,68,68,0.12);
    border: 1px solid rgba(239,68,68,0.3);
    border-radius: 10px;
    padding: 12px 16px;
    margin-bottom: 16px;
    display: none;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }
  .alert-banner.visible { display: flex; }
  .alert-text { color: var(--red); font-weight: 600; font-size: 0.9rem; }
  .toast {
    position: fixed;
    bottom: 20px;
    left: 50%;
    transform: translateX(-50%) translateY(80px);
    background: var(--surface2);
    color: var(--text);
    padding: 12px 24px;
    border-radius: 99px;
    font-size: 0.88rem;
    font-weight: 600;
    box-shadow: var(--shadow);
    transition: transform 0.3s ease;
    z-index: 1000;
    border: 1px solid rgba(255,255,255,0.1);
  }
  .toast.show { transform: translateX(-50%) translateY(0); }
  .divider { height: 1px; background: rgba(255,255,255,0.05); margin: 12px 0; }
  .small { font-size: 0.78rem; color: var(--muted); }
  .wifi-section { margin-top: 14px; }
</style>
</head>
<body>

<header>
  <div class="header-title">
    <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19.428 15.428a2 2 0 00-1.022-.547l-2.387-.477a6 6 0 00-3.86.517l-.318.158a6 6 0 01-3.86.517L6.05 15.21a2 2 0 00-1.806.547M8 4h8l-1 1v5.172a2 2 0 00.586 1.414l5 5c1.26 1.26.367 3.414-1.415 3.414H4.828c-1.782 0-2.674-2.154-1.414-3.414l5-5A2 2 0 009 10.172V5L8 4z"></path></svg>
    <h1>PillPal</h1>
  </div>
  <span id="wifiBadge" class="badge badge-yellow">Connecting...</span>
</header>

<!-- Missed dose alert banner -->
<div id="alertBanner" class="alert-banner">
  <span class="alert-text">⚠️ <span id="alertText">Missed dose detected!</span></span>
  <button class="success" onclick="ackAll()">✓ Acknowledge All</button>
</div>

<div class="grid">

  <!-- ── Clock Card ─────────────────────────────────────── -->
  <div class="card">
    <h2><span class="icon">🕐</span> Clock</h2>
    <div class="clock-display" id="clockDisplay">--:--:--</div>
    <div class="date-display" id="dateDisplay">Loading...</div>
    <div class="divider"></div>

    <div class="form-row">
      <label>Set Time</label>
      <input type="time" id="setTimeInput" step="1">
      <button onclick="setTime()">Set</button>
    </div>
    <div class="form-row">
      <label>Set Date</label>
      <input type="date" id="setDateInput">
      <button onclick="setDate()">Set</button>
    </div>
    <p class="small" id="rtcStatus">RTC status loading...</p>
  </div>

  <!-- ── Schedule Card ─────────────────────────────────── -->
  <div class="card">
    <h2><span class="icon">📅</span> Dose Schedule</h2>

    <div class="dose-row">
      <span class="dose-label">🌅 Morning</span>
      <span class="dose-time" id="timeMorning">--:--</span>
      <input type="time" id="inMorning">
      <button onclick="setSchedule('morning')">Save</button>
    </div>

    <div class="dose-row">
      <span class="dose-label">☀️ Midday</span>
      <span class="dose-time" id="timeMidday">--:--</span>
      <input type="time" id="inMidday">
      <button onclick="setSchedule('midday')">Save</button>
    </div>

    <div class="dose-row">
      <span class="dose-label">🌙 Night</span>
      <span class="dose-time" id="timeNight">--:--</span>
      <input type="time" id="inNight">
      <button onclick="setSchedule('night')">Save</button>
    </div>

    <div class="divider"></div>
    <div class="form-row">
      <label>Timeout</label>
      <input type="number" id="inTimeout" min="5" max="120" placeholder="minutes">
      <button class="ghost" onclick="setTimeout_()">Set</button>
    </div>
    <p class="small">Minutes before missed-dose alert fires.</p>
  </div>

  <!-- ── Manual Dispense Card ──────────────────────────── -->
  <div class="card">
    <h2><span class="icon">⚙️</span> Motors &amp; Dispense</h2>
    <div class="dispense-grid">

      <div class="dispense-card">
        <div class="motor-name">🌅 MORNING</div>
        <div class="slot-num" id="slotMorning">-</div>
        <div class="motor-state" id="stateMorning">-</div>
        <button onclick="dispense('morning')">Dispense</button>
        <br><br>
        <button class="warn ghost" onclick="home('morning')">Home</button>
      </div>

      <div class="dispense-card">
        <div class="motor-name">☀️ MIDDAY</div>
        <div class="slot-num" id="slotMidday">-</div>
        <div class="motor-state" id="stateMidday">-</div>
        <button onclick="dispense('midday')">Dispense</button>
        <br><br>
        <button class="warn ghost" onclick="home('midday')">Home</button>
      </div>

      <div class="dispense-card">
        <div class="motor-name">🌙 NIGHT</div>
        <div class="slot-num" id="slotNight">-</div>
        <div class="motor-state" id="stateNight">-</div>
        <button onclick="dispense('night')">Dispense</button>
        <br><br>
        <button class="warn ghost" onclick="home('night')">Home</button>
      </div>

    </div>
    <div style="margin-top:14px; display:flex; gap:8px;">
      <button class="warn" style="flex:1" onclick="home('all')">🏠 Home All</button>
      <button class="danger" style="flex:1" onclick="ackAll()">✓ Ack All</button>
    </div>
  </div>

  <!-- ── Status Card ───────────────────────────────────── -->
  <div class="card">
    <h2><span class="icon">📊</span> System Status</h2>

    <div class="status-row">
      <span class="status-key">Battery</span>
      <span class="status-val" id="batPct">--%</span>
    </div>
    <div class="battery-bar"><div class="battery-fill" id="batBar" style="width:0%"></div></div>

    <div class="status-row">
      <span class="status-key">WiFi</span>
      <span class="status-val" id="wifiStatus">--</span>
    </div>
    <div class="status-row">
      <span class="status-key">IP Address</span>
      <span class="status-val" id="ipAddr">--</span>
    </div>
    <div class="status-row">
      <span class="status-key">RTC</span>
      <span class="status-val" id="rtcMode">--</span>
    </div>
    <div class="status-row">
      <span class="status-key">Next Dose</span>
      <span class="status-val" id="nextDose">--</span>
    </div>
    <div class="status-row">
      <span class="status-key">Uptime</span>
      <span class="status-val" id="uptime">--</span>
    </div>

    <div class="divider"></div>

    <!-- WiFi Settings -->
    <div class="wifi-section">
      <h2><span class="icon">📶</span> WiFi Settings</h2>
      <div class="form-row">
        <input type="text" id="wifiSsid" placeholder="SSID">
      </div>
      <div class="form-row">
        <input type="password" id="wifiPass" placeholder="Password">
        <button onclick="saveWifi()">Connect</button>
      </div>
      <p class="small">Device will reboot to connect.</p>
    </div>
  </div>

</div><!-- /grid -->

<div id="toast" class="toast"></div>

<script>
// ── State ─────────────────────────────────────────────────
let pollTimer = null;

// ── Helpers ───────────────────────────────────────────────
function toast(msg, color='#4f8ef7') {
  const el = document.getElementById('toast');
  el.textContent = msg;
  el.style.borderColor = color;
  el.classList.add('show');
  setTimeout(() => el.classList.remove('show'), 2800);
}

function pad(n){ return String(n).padStart(2,'0'); }

async function api(path, body=null) {
  try {
    const opts = body
      ? { method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify(body) }
      : { method:'GET' };
    const r = await fetch(path, opts);
    return await r.json();
  } catch(e) {
    toast('Connection error: '+e.message, '#ef4444');
    return null;
  }
}

// ── State class mapping ───────────────────────────────────
function stateClass(s) {
  const m = {HOMED:'state-homed', HOMING:'state-homing',
              DISPENSING:'state-dispensing', FAULT:'state-fault', IDLE:'state-idle'};
  return m[s] || 'state-idle';
}

// ── Poll /api/status every 2s ─────────────────────────────
async function poll() {
  const d = await api('/api/status');
  if (!d) return;

  // Clock
  document.getElementById('clockDisplay').textContent =
    `${pad(d.time.hh)}:${pad(d.time.mm)}:${pad(d.time.ss)}`;
  document.getElementById('dateDisplay').textContent =
    `${d.time.dow}  ${pad(d.time.dd)}/${pad(d.time.mo)}/${d.time.yr}`;
  document.getElementById('rtcStatus').textContent =
    d.rtcOk ? '✓ DS3231 RTC active' : '⚠ millis() fallback — set time!';
  document.getElementById('rtcMode').textContent = d.rtcOk ? 'DS3231 OK' : 'Fallback';

  // Schedule
  document.getElementById('timeMorning').textContent = d.schedule.morning;
  document.getElementById('timeMidday').textContent  = d.schedule.midday;
  document.getElementById('timeNight').textContent   = d.schedule.night;

  // Motors
  ['morning','midday','night'].forEach(k => {
    const m = d.motors[k];
    document.getElementById('slot'+cap(k)).textContent  = 'Slot '+m.slot;
    const stEl = document.getElementById('state'+cap(k));
    stEl.textContent = m.state;
    stEl.className = 'motor-state '+stateClass(m.state);
  });

  // Battery
  document.getElementById('batPct').textContent = d.battery+'%';
  document.getElementById('batBar').style.width = d.battery+'%';

  // WiFi
  document.getElementById('wifiStatus').textContent = d.wifiMode;
  document.getElementById('ipAddr').textContent     = d.ip;
  document.getElementById('wifiBadge').textContent  = d.wifiMode;
  document.getElementById('wifiBadge').className    =
    'badge ' + (d.wifiMode==='Station' ? 'badge-green' : 'badge-yellow');

  // Next dose
  document.getElementById('nextDose').textContent =
    d.nextDose ? `${d.nextDose.name} at ${d.nextDose.time} (${d.nextDose.in}m)` : 'None today';

  // Uptime
  const u = Math.floor(d.uptimeMs/1000);
  document.getElementById('uptime').textContent =
    `${Math.floor(u/3600)}h ${Math.floor((u%3600)/60)}m ${u%60}s`;

  // Missed dose alert banner
  const missed = d.missed && d.missed.length > 0;
  document.getElementById('alertBanner').classList.toggle('visible', missed);
  if (missed)
    document.getElementById('alertText').textContent = '⚠️ Missed: ' + d.missed.join(', ');
}

function cap(s){ return s.charAt(0).toUpperCase()+s.slice(1); }

// ── Set Time ──────────────────────────────────────────────
async function setTime() {
  const val = document.getElementById('setTimeInput').value;
  if (!val) { toast('Pick a time first','#f59e0b'); return; }
  const [hh,mm,ss='0'] = val.split(':');
  const r = await api('/api/time', {hh:+hh,mm:+mm,ss:+ss});
  toast(r && r.ok ? '✓ Time updated' : '✗ Failed', r&&r.ok?'#22c55e':'#ef4444');
}

async function setDate() {
  const val = document.getElementById('setDateInput').value;
  if (!val) { toast('Pick a date first','#f59e0b'); return; }
  const [yr,mo,dd] = val.split('-');
  const r = await api('/api/time', {dd:+dd,mo:+mo,yr:+yr});
  toast(r && r.ok ? '✓ Date updated' : '✗ Failed', r&&r.ok?'#22c55e':'#ef4444');
}

// ── Set Schedule ──────────────────────────────────────────
async function setSchedule(dose) {
  const val = document.getElementById('in'+cap(dose)).value;
  if (!val) { toast('Pick a time first','#f59e0b'); return; }
  const body = {}; body[dose] = val;
  const r = await api('/api/schedule', body);
  toast(r && r.ok ? `✓ ${cap(dose)} dose set to ${val}` : '✗ Failed',
        r&&r.ok?'#22c55e':'#ef4444');
}

async function setTimeout_() {
  const v = parseInt(document.getElementById('inTimeout').value);
  if (!v || v<5||v>120) { toast('Timeout must be 5-120 min','#f59e0b'); return; }
  const r = await api('/api/schedule', {timeout: v});
  toast(r && r.ok ? `✓ Timeout set to ${v} min` : '✗ Failed',
        r&&r.ok?'#22c55e':'#ef4444');
}

// ── Dispense ──────────────────────────────────────────────
async function dispense(dose) {
  if (!confirm(`Manually dispense ${dose} dose?`)) return;
  const r = await api('/api/dispense', {dose});
  toast(r && r.ok ? `✓ ${cap(dose)} dispensing...` : '✗ ' + (r&&r.error||'Failed'),
        r&&r.ok?'#4f8ef7':'#ef4444');
}

// ── Home ──────────────────────────────────────────────────
async function home(motor) {
  const r = await api('/api/home', {motor});
  toast(r && r.ok ? `✓ Homing ${motor}...` : '✗ Failed',
        r&&r.ok?'#f59e0b':'#ef4444');
}

// ── Acknowledge ───────────────────────────────────────────
async function ackAll() {
  const r = await api('/api/ack', {dose:'all'});
  toast(r && r.ok ? '✓ All doses acknowledged' : '✗ Failed',
        r&&r.ok?'#22c55e':'#ef4444');
}

// ── Save WiFi ─────────────────────────────────────────────
async function saveWifi() {
  const ssid = document.getElementById('wifiSsid').value.trim();
  const pass = document.getElementById('wifiPass').value;
  if (!ssid) { toast('Enter SSID','#f59e0b'); return; }
  if (!confirm(`Connect to "${ssid}"?\nDevice will reboot.`)) return;
  const r = await api('/api/wifi', {ssid, pass});
  if (r && r.ok) {
    toast('✓ Saved! Rebooting...', '#22c55e');
  } else {
    toast('✗ Failed','#ef4444');
  }
}

// ── Start polling ─────────────────────────────────────────
poll();
setInterval(poll, 2000);
</script>
</body>
</html>
)rawhtml";