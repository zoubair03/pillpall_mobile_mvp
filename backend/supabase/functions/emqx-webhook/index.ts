// Receives EMQX rule-engine webhooks for pillpal/{device_uid}/status and
// pillpal/{device_uid}/event/dose, and writes the corresponding row via the
// service-role client (this is the trusted device/broker path — RLS does
// not apply here, unlike the app-facing functions).
//
// Expected body shape (configured on the EMQX rule's webhook action):
//   { "topic": "pillpal/{device_uid}/status" | "pillpal/{device_uid}/event/dose",
//     "payload": { ...device-reported JSON... } }
//
// This runs unattended off real hardware. A single malformed or
// unrecognized message must never wedge the pipeline, so anything that
// looks like a bad message (parse failure, unknown topic, unknown
// device_uid, invalid field values) is logged and answered with 200 so
// EMQX doesn't retry it forever. Actual infra failures (DB unreachable,
// write error) return 500 so EMQX's own retry policy can kick in.

import { createClient } from "jsr:@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const WEBHOOK_SECRET = Deno.env.get("EMQX_WEBHOOK_SECRET")!;

const supabase = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);

const TOPIC_RE = /^pillpal\/([^/]+)\/(status|event\/dose)$/;

const DOSE_TYPES = new Set(["morning", "midday", "night"]);
const DOSE_STATUSES = new Set(["pending", "dispensed", "acknowledged", "missed"]);

Deno.serve(async (req) => {
  try {
    if (req.headers.get("x-webhook-secret") !== WEBHOOK_SECRET) {
      console.error("emqx-webhook: rejected request with missing/invalid X-Webhook-Secret");
      return new Response("unauthorized", { status: 401 });
    }

    let body: unknown;
    try {
      body = await req.json();
    } catch (err) {
      console.error("emqx-webhook: failed to parse request body as JSON:", err);
      return new Response("ok", { status: 200 });
    }

    const { topic, payload } = (body ?? {}) as { topic?: unknown; payload?: unknown };

    if (typeof topic !== "string") {
      console.error("emqx-webhook: missing/invalid 'topic' field:", body);
      return new Response("ok", { status: 200 });
    }

    const match = TOPIC_RE.exec(topic);
    if (!match) {
      console.error(`emqx-webhook: unrecognized topic shape: ${topic}`);
      return new Response("ok", { status: 200 });
    }

    if (typeof payload !== "object" || payload === null) {
      console.error(`emqx-webhook: missing/invalid 'payload' for topic ${topic}`);
      return new Response("ok", { status: 200 });
    }

    const [, deviceUid, kind] = match;

    const { data: device, error: deviceErr } = await supabase
      .from("devices")
      .select("id")
      .eq("device_uid", deviceUid)
      .maybeSingle();

    if (deviceErr) {
      console.error(`emqx-webhook: device lookup failed for device_uid ${deviceUid}:`, deviceErr);
      return new Response("lookup failed", { status: 500 });
    }

    if (!device) {
      console.error(`emqx-webhook: unrecognized device_uid: ${deviceUid}`);
      return new Response("ok", { status: 200 });
    }

    if (kind === "status") {
      return await handleStatus(device.id, payload as Record<string, unknown>);
    }
    return await handleDoseEvent(device.id, payload as Record<string, unknown>);
  } catch (err) {
    console.error("emqx-webhook: unexpected error handling request:", err);
    return new Response("ok", { status: 200 });
  }
});

async function handleStatus(deviceId: string, payload: Record<string, unknown>): Promise<Response> {
  const row = {
    device_id: deviceId,
    battery_pct: numberOrNull(payload.battery_pct),
    wifi_connected: booleanOrNull(payload.wifi_connected),
    wifi_ip: stringOrNull(payload.wifi_ip),
    rtc_ok: booleanOrNull(payload.rtc_ok),
    motor_morning_state: stringOrNull(payload.motor_morning_state),
    motor_midday_state: stringOrNull(payload.motor_midday_state),
    motor_night_state: stringOrNull(payload.motor_night_state),
    updated_at: new Date().toISOString(),
  };

  const { error } = await supabase.from("device_status").upsert(row, { onConflict: "device_id" });

  if (error) {
    console.error(`emqx-webhook: failed to upsert device_status for device ${deviceId}:`, error);
    return new Response("upsert failed", { status: 500 });
  }

  return new Response("ok", { status: 200 });
}

async function handleDoseEvent(deviceId: string, payload: Record<string, unknown>): Promise<Response> {
  const dose = payload.dose;
  const status = payload.status;

  if (typeof dose !== "string" || !DOSE_TYPES.has(dose)) {
    console.error(`emqx-webhook: invalid 'dose' in dose event for device ${deviceId}:`, payload);
    return new Response("ok", { status: 200 });
  }

  if (typeof status !== "string" || !DOSE_STATUSES.has(status)) {
    console.error(`emqx-webhook: invalid 'status' in dose event for device ${deviceId}:`, payload);
    return new Response("ok", { status: 200 });
  }

  const row: Record<string, unknown> = {
    device_id: deviceId,
    dose,
    status,
    source: "device",
  };

  if (typeof payload.scheduled_at === "string") row.scheduled_at = payload.scheduled_at;
  if (typeof payload.occurred_at === "string") row.occurred_at = payload.occurred_at;

  const { error } = await supabase.from("dose_events").insert(row);

  if (error) {
    console.error(`emqx-webhook: failed to insert dose_event for device ${deviceId}:`, error);
    return new Response("insert failed", { status: 500 });
  }

  return new Response("ok", { status: 200 });
}

function numberOrNull(v: unknown): number | null {
  return typeof v === "number" ? v : null;
}
function stringOrNull(v: unknown): string | null {
  return typeof v === "string" ? v : null;
}
function booleanOrNull(v: unknown): boolean | null {
  return typeof v === "boolean" ? v : null;
}
