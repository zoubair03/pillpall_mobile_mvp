// Called by the app (caller's own JWT) to send a command down to a device.
// Input: { device_id, command: "update_schedule" | "ack_dose" |
//   "force_dispense" | "calibrate" | "restart", payload }
//
// This function does NOT write to schedules/dose_events itself — the app
// updates schedules directly (it already has an RLS update policy for
// that), and an ack_dose/force_dispense command is expected to make the
// device publish its own event/dose message, which emqx-webhook then
// records. This function's only job is: confirm the caller actually has
// access to the device, then relay the command over MQTT via EMQX's HTTP
// publish API.
//
// Unlike emqx-webhook (a webhook that must never wedge on a bad message),
// this is called directly by the app, so normal HTTP error semantics
// apply: 400 for bad input, 401 unauthenticated, 403 unauthorized,
// 502 if the broker call itself fails.

import { createClient } from "jsr:@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY")!;
const EMQX_API_URL = Deno.env.get("EMQX_API_URL")!;
const EMQX_API_KEY = Deno.env.get("EMQX_API_KEY")!;
const EMQX_API_SECRET = Deno.env.get("EMQX_API_SECRET")!;

const ALLOWED_COMMANDS = new Set([
  "update_schedule",
  "ack_dose",
  "force_dispense",
  "calibrate",
  "restart",
]);

function json(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

Deno.serve(async (req) => {
  if (req.method !== "POST") {
    return json({ error: "method not allowed" }, 405);
  }

  const authHeader = req.headers.get("Authorization");
  if (!authHeader) {
    return json({ error: "missing authorization header" }, 401);
  }

  const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
    global: { headers: { Authorization: authHeader } },
  });

  const { data: userData, error: userErr } = await supabase.auth.getUser();
  if (userErr || !userData.user) {
    console.error("publish-command: invalid/expired token:", userErr);
    return json({ error: "invalid or expired token" }, 401);
  }
  const caller = userData.user;

  let body: unknown;
  try {
    body = await req.json();
  } catch (err) {
    console.error("publish-command: failed to parse request body as JSON:", err);
    return json({ error: "invalid JSON body" }, 400);
  }

  const { device_id, command, payload } = (body ?? {}) as {
    device_id?: unknown;
    command?: unknown;
    payload?: unknown;
  };

  if (typeof device_id !== "string" || device_id.length === 0) {
    return json({ error: "device_id is required" }, 400);
  }
  if (typeof command !== "string" || !ALLOWED_COMMANDS.has(command)) {
    return json({ error: `command must be one of: ${[...ALLOWED_COMMANDS].join(", ")}` }, 400);
  }
  const commandPayload = typeof payload === "object" && payload !== null ? payload : {};

  // RLS-scoped lookup: the "read linked devices" policy means this simply
  // returns no row if the caller isn't linked to this device via
  // caregiver_patients — that's what gives us the 403 for free, and keeps
  // "device doesn't exist" and "device isn't yours" indistinguishable.
  const { data: device, error: deviceErr } = await supabase
    .from("devices")
    .select("id, device_uid")
    .eq("id", device_id)
    .maybeSingle();

  if (deviceErr) {
    console.error(`publish-command: device lookup failed for caller ${caller.id}, device ${device_id}:`, deviceErr);
    return json({ error: "internal error" }, 500);
  }

  if (!device) {
    console.error(`publish-command: caller ${caller.id} denied access to device ${device_id}`);
    return json({ error: "forbidden" }, 403);
  }

  const emqxRes = await fetch(`${EMQX_API_URL}/publish`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: "Basic " + btoa(`${EMQX_API_KEY}:${EMQX_API_SECRET}`),
    },
    body: JSON.stringify({
      topic: `pillpal/${device.device_uid}/cmd`,
      qos: 1,
      payload: JSON.stringify({ command, payload: commandPayload }),
      payload_encoding: "plain",
    }),
  }).catch((err) => {
    console.error(`publish-command: EMQX publish request failed for device ${device.device_uid}:`, err);
    return null;
  });

  if (!emqxRes || !emqxRes.ok) {
    const detail = emqxRes ? await emqxRes.text() : "network error";
    console.error(`publish-command: EMQX publish failed for device ${device.device_uid}: ${detail}`);
    return json({ error: "failed to publish command to device" }, 502);
  }

  return json({ ok: true }, 200);
});
