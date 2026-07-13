// Scheduled function — not called by the app or the device. For every
// schedule row, checks whether time_of_day + missed_timeout_min has
// passed today (in the *patient's* timezone, not server/UTC time) with
// no dispensed/acknowledged dose_event. Idempotent: skips doses already
// marked missed today so reruns don't re-alert. For each real miss:
// inserts a missed dose_event, an alerts row, then pushes to every
// caregiver linked to that device's patient via caregiver_push_tokens.
//
// Not triggered via a webhook secret or the app's JWT — only whoever
// holds the service role key (our own cron trigger) can call this.
// verify_jwt stays at its platform default (true); the header check
// below is the actual access control.
//
// One bad schedule row (bad timezone, weird data) must not abort the
// whole run — each row is processed in its own try/catch. A caregiver
// with no registered push token, or unset/invalid FCM credentials, just
// means the push step logs and is skipped — the dose_event/alert writes
// (the part that matters for the app's own data) still happen.

import { createClient } from "jsr:@supabase/supabase-js@2";
import { DateTime } from "npm:luxon@3";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const FCM_PROJECT_ID = Deno.env.get("FCM_PROJECT_ID")!;
const FCM_CLIENT_EMAIL = Deno.env.get("FCM_CLIENT_EMAIL")!;
const FCM_PRIVATE_KEY = Deno.env.get("FCM_PRIVATE_KEY")!;

const supabase = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);

function json(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

Deno.serve(async (req) => {
  const authHeader = req.headers.get("Authorization");
  if (authHeader !== `Bearer ${SERVICE_ROLE_KEY}`) {
    console.error("check-missed-doses: rejected request without valid service-role authorization");
    return json({ error: "unauthorized" }, 401);
  }

  const { data: schedules, error: schedulesErr } = await supabase
    .from("schedules")
    .select("id, dose, time_of_day, missed_timeout_min, device_id, devices(device_uid, patient_id, patients(timezone))");

  if (schedulesErr) {
    console.error("check-missed-doses: failed to load schedules:", schedulesErr);
    return json({ error: "internal error" }, 500);
  }

  let checked = 0;
  let missed = 0;

  for (const schedule of schedules ?? []) {
    checked++;
    try {
      if (await processSchedule(schedule)) missed++;
    } catch (err) {
      console.error(`check-missed-doses: unexpected error processing schedule ${schedule.id}:`, err);
    }
  }

  console.log(`check-missed-doses: checked ${checked} schedules, recorded ${missed} new misses`);
  return json({ checked, missed }, 200);
});

// deno-lint-ignore no-explicit-any
async function processSchedule(schedule: any): Promise<boolean> {
  const device = schedule.devices;
  if (!device?.patient_id || !device.patients) {
    return false; // unclaimed device — nothing to check or alert
  }

  const timezone: string = device.patients.timezone;
  const nowInTz = DateTime.now().setZone(timezone);
  if (!nowInTz.isValid) {
    console.error(`check-missed-doses: invalid timezone "${timezone}" for device ${device.device_uid}`);
    return false;
  }

  const [hh, mm, ss] = String(schedule.time_of_day).split(":").map((p: string) => parseInt(p, 10) || 0);
  const scheduledToday = nowInTz.set({ hour: hh, minute: mm, second: ss, millisecond: 0 });
  const missedCutoff = scheduledToday.plus({ minutes: schedule.missed_timeout_min });

  if (nowInTz < missedCutoff) {
    return false; // not due yet
  }

  const dayStart = scheduledToday.startOf("day").toUTC().toISO();
  const dayEnd = scheduledToday.endOf("day").toUTC().toISO();

  const { data: existing, error: existingErr } = await supabase
    .from("dose_events")
    .select("status")
    .eq("device_id", schedule.device_id)
    .eq("dose", schedule.dose)
    .gte("occurred_at", dayStart)
    .lte("occurred_at", dayEnd);

  if (existingErr) {
    console.error(`check-missed-doses: failed to check existing dose_events for schedule ${schedule.id}:`, existingErr);
    return false;
  }

  // deno-lint-ignore no-explicit-any
  const alreadyHandled = (existing ?? []).some((e: any) =>
    e.status === "dispensed" || e.status === "acknowledged" || e.status === "missed"
  );
  if (alreadyHandled) return false;

  const { error: insertEventErr } = await supabase.from("dose_events").insert({
    device_id: schedule.device_id,
    dose: schedule.dose,
    status: "missed",
    scheduled_at: scheduledToday.toUTC().toISO(),
    source: "system",
  });

  if (insertEventErr) {
    console.error(`check-missed-doses: failed to insert missed dose_event for schedule ${schedule.id}:`, insertEventErr);
    return false;
  }

  const { error: insertAlertErr } = await supabase.from("alerts").insert({
    device_id: schedule.device_id,
    kind: "missed_dose",
    message: `Missed ${schedule.dose} dose`,
  });

  if (insertAlertErr) {
    console.error(`check-missed-doses: failed to insert alert for schedule ${schedule.id}:`, insertAlertErr);
  }

  await notifyCaregivers(device.patient_id, schedule.dose, device.device_uid);

  return true;
}

async function notifyCaregivers(patientId: string, dose: string, deviceUid: string): Promise<void> {
  const { data: links, error: linksErr } = await supabase
    .from("caregiver_patients")
    .select("caregiver_id")
    .eq("patient_id", patientId);

  if (linksErr) {
    console.error(`check-missed-doses: failed to load caregivers for patient ${patientId}:`, linksErr);
    return;
  }

  const caregiverIds = (links ?? []).map((l) => l.caregiver_id);
  if (caregiverIds.length === 0) return;

  const { data: tokens, error: tokensErr } = await supabase
    .from("caregiver_push_tokens")
    .select("fcm_token")
    .in("caregiver_id", caregiverIds);

  if (tokensErr) {
    console.error(`check-missed-doses: failed to load push tokens for patient ${patientId}:`, tokensErr);
    return;
  }

  if (!tokens || tokens.length === 0) return;

  let accessToken: string;
  try {
    accessToken = await getFcmAccessToken();
  } catch (err) {
    console.error("check-missed-doses: failed to obtain FCM access token, skipping push:", err);
    return;
  }

  const title = "Missed dose";
  const body = `Device ${deviceUid} missed the ${dose} dose.`;

  await Promise.all(tokens.map((t) => sendPush(accessToken, t.fcm_token, title, body)));
}

async function sendPush(accessToken: string, fcmToken: string, title: string, body: string): Promise<void> {
  const res = await fetch(`https://fcm.googleapis.com/v1/projects/${FCM_PROJECT_ID}/messages:send`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({ message: { token: fcmToken, notification: { title, body } } }),
  }).catch((err) => {
    console.error("check-missed-doses: FCM request failed:", err);
    return null;
  });

  if (!res) return;
  if (!res.ok) {
    console.error(`check-missed-doses: FCM push failed (${res.status}): ${await res.text()}`);
  }
}

let cachedToken: { token: string; expiresAt: number } | null = null;

async function getFcmAccessToken(): Promise<string> {
  if (cachedToken && cachedToken.expiresAt > Date.now() + 60_000) {
    return cachedToken.token;
  }
  const token = await fetchFcmAccessToken();
  cachedToken = { token, expiresAt: Date.now() + 55 * 60 * 1000 };
  return token;
}

async function fetchFcmAccessToken(): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = base64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = base64url(JSON.stringify({
    iss: FCM_CLIENT_EMAIL,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  const signingInput = `${header}.${claims}`;

  const key = await importPrivateKey(FCM_PRIVATE_KEY);
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(signingInput),
  );
  const jwt = `${signingInput}.${base64urlBytes(new Uint8Array(signature))}`;

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });

  const data = await res.json();
  if (!res.ok || !data.access_token) {
    throw new Error(`FCM token exchange failed: ${JSON.stringify(data)}`);
  }
  return data.access_token;
}

async function importPrivateKey(pem: string): Promise<CryptoKey> {
  const normalized = pem.includes("\\n") ? pem.replace(/\\n/g, "\n") : pem;
  const body = normalized
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s+/g, "");
  const binary = atob(body);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return crypto.subtle.importKey(
    "pkcs8",
    bytes.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

function base64url(str: string): string {
  return base64urlBytes(new TextEncoder().encode(str));
}

function base64urlBytes(bytes: Uint8Array): string {
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
