#!/usr/bin/env node
// Local bridge: take an APK produced by CI, install it on the physical device
// over wireless adb, and run a non-destructive smoke test.
//
//   node scripts/device-smoke.mjs <apk> [--ip 192.168.2.55] [--port 41587]
//                                      [--package app.olauncher.debug]
//                                      [--set-home]
//
// By default this DOES NOT change the phone's default launcher. Pass
// --set-home only when you are deliberately testing the HOME role; to restore:
//   adb shell cmd role remove-role-holder android.app.role.HOME <package>
// or simply pick the original launcher in Settings > Apps > Default apps.
//
// Optional env: ADB (full path to adb binary), DEVICE_IP.

import { execFileSync, spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import os from 'node:os';

const args = process.argv.slice(2);
const flag = (name, fallback) => {
  const i = args.indexOf(name);
  return i >= 0 && args[i + 1] && !args[i + 1].startsWith('--') ? args[i + 1] : fallback;
};
const apkPath = args.find((a) => !a.startsWith('--') && a.toLowerCase().endsWith('.apk'));
const APK = resolve(apkPath || '');
if (!apkPath || !existsSync(APK)) {
  console.error('usage: node scripts/device-smoke.mjs <apk> [--ip IP] [--port PORT] [--package PKG] [--set-home]');
  console.error(`apk not found: ${apkPath || '(missing)'}`);
  process.exit(2);
}

const IP = flag('--ip', process.env.DEVICE_IP || '192.168.2.55');
const PKG = flag('--package', 'app.olauncher.debug');
const ACTIVITY = flag('--activity', 'app.olauncher.MainActivity');
const SET_HOME = args.includes('--set-home');
const OUT = join(os.tmpdir(), 'ddzm-device-smoke');
mkdirSync(OUT, { recursive: true });

const win = process.platform === 'win32';
const adbCandidates = [
  process.env.ADB,
  join(os.tmpdir(), 'ddzm-adb', 'platform-tools', win ? 'adb.exe' : 'adb'),
  join(os.homedir(), 'AppData', 'Local', 'Temp', 'ddzm-adb', 'platform-tools', 'adb.exe'),
  join(os.homedir(), 'Android', 'Sdk', 'platform-tools', win ? 'adb.exe' : 'adb'),
].filter(Boolean);
const ADB = adbCandidates.find((p) => existsSync(p)) || 'adb';

function adb(...params) {
  return execFileSync(ADB, params, { encoding: 'utf8', timeout: 120_000 }).trim();
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const step = (s) => console.log(`\n== ${s} ==`);

step(`adb: ${ADB}`);
try {
  console.log(adb('version').split('\n')[0]);
} catch (e) {
  console.error('adb not usable. Set ADB=<path to adb> and retry.');
  process.exit(2);
}

// Wireless debugging ports rotate on every toggle; discover via mDNS.
let port = flag('--port', null);
if (!port) {
  step('discovering wireless debugging port via mDNS');
  for (let i = 0; i < 6 && !port; i += 1) {
    const out = adb('mdns', 'services');
    const line = out.split('\n').find((l) => l.includes('_adb-tls-connect._tcp'));
    if (line) {
      const m = line.match(/:(\d+)\s*$/);
      if (m) port = m[1];
    }
    if (!port) await sleep(700);
  }
}
if (!port) {
  console.error('no _adb-tls-connect._tcp service found. Keep the phone unlocked and Wireless debugging ON, or pass --port.');
  process.exit(2);
}

const SERIAL = `${IP}:${port}`;
step(`connect ${SERIAL}`);
console.log(adb('connect', SERIAL));
const devices = adb('devices');
if (!new RegExp(`^${SERIAL.replaceAll('.', '\\.')}\\s+device`, 'm').test(devices)) {
  console.error('device not authorised:\n' + devices);
  process.exit(2);
}
const on = (...p) => adb('-s', SERIAL, ...p);

step('device info');
console.log(on('shell', 'getprop ro.product.model; getprop ro.build.version.release; getprop ro.build.version.sdk'));

step(`install ${APK}`);
console.log(on('install', '-r', '-t', APK));

step('launch and observe');
on('logcat', '-c');
console.log(on('shell', `am start -n ${PKG}/${ACTIVITY}`));
await sleep(5000);

const pid = on('shell', `pidof ${PKG}`) || '';
const logcat = on('logcat', '-d', '-t', '600');
const activities = on('shell', 'dumpsys activity activities');
const focusLines = activities.split('\n').filter((l) => /ResumedActivity|topResumed/i.test(l)).slice(0, 8).join('\n');
writeFileSync(join(OUT, 'logcat.txt'), logcat);
writeFileSync(join(OUT, 'pid.txt'), pid);
writeFileSync(join(OUT, 'activities.txt'), focusLines);

const shot = spawnSync(ADB, ['-s', SERIAL, 'exec-out', 'screencap', '-p'], { timeout: 60_000 });
if (shot.status === 0 && shot.stdout?.length) {
  writeFileSync(join(OUT, 'launcher.png'), shot.stdout);
}

console.log(`pid: ${pid || '(none)'}`);
console.log(`focus:\n${focusLines || '(no resumed activity line)'}`);

let homeResult = 'skipped (default launcher untouched)';
if (SET_HOME) {
  step('setting HOME role (explicit --set-home)');
  try {
    console.log(on('shell', `cmd role add-role-holder android.app.role.HOME ${PKG}`));
    homeResult = 'granted via cmd role';
  } catch {
    try {
      console.log(on('shell', `cmd package set-home-activity ${PKG}/${ACTIVITY}`));
      homeResult = 'granted via set-home-activity';
    } catch {
      homeResult = 'FAILED (OEM refused shell role change)';
    }
  }
  const holders = on('shell', 'cmd role get-role-holders android.app.role.HOME');
  console.log(`holders: ${holders}`);
  writeFileSync(join(OUT, 'home-holders.txt'), holders);
  on('shell', 'input keyevent KEYCODE_HOME');
  await sleep(2000);
  const shot2 = spawnSync(ADB, ['-s', SERIAL, 'exec-out', 'screencap', '-p'], { timeout: 60_000 });
  if (shot2.status === 0 && shot2.stdout?.length) writeFileSync(join(OUT, 'after-home.png'), shot2.stdout);
}

const crashed = /FATAL EXCEPTION/.test(logcat);
step('result');
console.log(`process_running : ${pid ? 'yes' : 'NO'}`);
console.log(`crash_in_logcat : ${crashed ? 'YES' : 'no'}`);
console.log(`home_role       : ${homeResult}`);
console.log(`artifacts       : ${OUT}`);
if (crashed || !pid) {
  const tail = logcat.split('\n').filter((l) => /FATAL|AndroidRuntime|ddzm|Ddzm/i.test(l)).slice(-30).join('\n');
  console.error(tail);
  process.exit(1);
}
console.log('SMOKE OK');
