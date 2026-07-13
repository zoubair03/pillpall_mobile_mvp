#!/usr/bin/env node
// Provisions one PillPal device: generates a device_uid + pairing_code
// and inserts an unclaimed row into `devices` (patient_id/claimed_at
// stay null, onboarding_completed stays false — exactly the shape
// create_patient_and_claim() expects to find and claim later).
//
// Deliberately zero npm dependencies, matching this backend's existing
// "just the Supabase CLI" convention (see package.json) — uses Node's
// built-in fetch against the REST API directly instead of pulling in
// @supabase/supabase-js for one script.
//
// Usage:
//   SUPABASE_URL=... SUPABASE_SERVICE_ROLE_KEY=... node scripts/provision-device.mjs
//
// Both env vars must be set by whoever runs this — never hardcoded and
// never read from any file this script itself creates or logs.
//
// This only creates the backend `devices` row. The second half of
// provisioning — pushing the same device_uid/pairing_code onto the
// physical unit via its POST /api/provision (see frimware_v0) — is not
// done here: it requires being connected to that unit's own setup
// hotspot at flash time, which isn't something a backend script can do
// blind. Run this first, note the printed values, then provision the
// unit itself separately once real hardware is available.

const SUPABASE_URL = process.env.SUPABASE_URL;
const SERVICE_ROLE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY;

if (!SUPABASE_URL || !SERVICE_ROLE_KEY) {
  console.error("Missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY in the environment.");
  process.exit(1);
}

function randomDeviceUid() {
  const suffix = crypto.randomUUID().split("-")[0];
  return `pillpal-${suffix}`;
}

function randomPairingCode() {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I — avoids visual mixups
  const bytes = crypto.getRandomValues(new Uint8Array(6));
  return Array.from(bytes, (b) => alphabet[b % alphabet.length]).join("");
}

const deviceUid = randomDeviceUid();
const pairingCode = randomPairingCode();

const response = await fetch(`${SUPABASE_URL}/rest/v1/devices`, {
  method: "POST",
  headers: {
    "Content-Type": "application/json",
    apikey: SERVICE_ROLE_KEY,
    Authorization: `Bearer ${SERVICE_ROLE_KEY}`,
    Prefer: "return=representation",
  },
  body: JSON.stringify({ device_uid: deviceUid, pairing_code: pairingCode }),
});

if (!response.ok) {
  console.error(`Provisioning failed (${response.status}): ${await response.text()}`);
  process.exit(1);
}

const [row] = await response.json();
console.log("Provisioned a new unclaimed device:");
console.log(`  device_uid:   ${row.device_uid}`);
console.log(`  pairing_code: ${row.pairing_code}`);
console.log("\nNext: push these same two values onto the physical unit via POST /api/provision");
console.log("while connected to its setup hotspot (see frimware_v0/src/web_server.cpp).");
