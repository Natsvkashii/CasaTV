import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import { config } from './config.js';
import { getAllSettings, getSetting, getStats } from './db.js';

/**
 * In-memory registry of connected clients (Android TV boxes, phones, browsers).
 * Clients send a heartbeat at least every minute; entries older than TTL are
 * dropped so "connected devices" only shows what is really online.
 */
const clients = new Map();
const CLIENT_TTL_MS = 90_000;
const startedAt = Date.now();

const SEVERITY = { ok: 'ok', warning: 'warning', critical: 'critical' };

function safeNumber(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

function clean(value, max) {
  return String(value || '').replace(/\s+/g, ' ').trim().slice(0, max);
}

function clientId(body, req) {
  const explicit = clean(body?.deviceId || body?.id, 80);
  if (explicit) return explicit;
  const fallback = clean(body?.name, 40) || clean(req?.ip, 40) || 'anonymous';
  return `${fallback}-${clean(req?.ip, 40)}`;
}

export function touchClient(req, body = {}) {
  const now = Date.now();
  const id = clientId(body, req);
  const previous = clients.get(id) || {};
  const profileName = clean(body.profileName, 40) || previous.profileName || '';
  const channel = clean(body.channel, 80);
  const client = {
    id,
    name: clean(body.name, 60) || previous.name || 'Dispositivo',
    platform: clean(body.platform, 40) || previous.platform || 'Android TV',
    appVersion: clean(body.appVersion, 32) || previous.appVersion || '',
    profileId: clean(body.profileId, 80) || previous.profileId || '',
    profileName,
    state: body.state === 'playing' ? 'playing' : 'idle',
    channel: body.state === 'playing' ? channel : '',
    ip: clean(req?.ip, 64) || previous.ip || '',
    firstSeen: previous.firstSeen || Math.floor(now / 1000),
    lastSeen: Math.floor(now / 1000),
  };
  clients.set(id, client);
  purgeClients(now);
  return client;
}

export function removeClient(id) {
  return clients.delete(String(id || ''));
}

function purgeClients(now = Date.now()) {
  for (const [id, client] of clients) {
    if (now - client.lastSeen * 1000 > CLIENT_TTL_MS) clients.delete(id);
  }
}

export function listClients() {
  purgeClients();
  return [...clients.values()].sort((a, b) => b.lastSeen - a.lastSeen);
}

function percent(used, total) {
  if (!total) return 0;
  return Math.max(0, Math.min(100, Math.round((used / total) * 1000) / 10));
}

function diskUsage() {
  try {
    const stat = fs.statfsSync(config.dataDir);
    const blockSize = safeNumber(stat.bsize);
    const total = safeNumber(stat.blocks) * blockSize;
    const free = safeNumber(stat.bavail) * blockSize;
    return {
      total,
      free,
      used: Math.max(0, total - free),
      percent: percent(Math.max(0, total - free), total),
    };
  } catch {
    return { total: 0, free: 0, used: 0, percent: 0 };
  }
}

function lastEpgFetch() {
  try {
    const row = getSetting('last_epg_fetch', '0');
    return safeNumber(row) || null;
  } catch {
    return null;
  }
}

function buildAlerts({ cpu, memory, disk }) {
  const alerts = [];
  const check = (kind, label, value) => {
    if (value >= 90) {
      alerts.push({
        level: SEVERITY.critical,
        kind,
        title: label,
        message: `${label}: ${value}%`,
        value,
      });
    } else if (value >= 75) {
      alerts.push({
        level: SEVERITY.warning,
        kind,
        title: label,
        message: `${label}: ${value}%`,
        value,
      });
    }
  };
  check('cpu', 'CPU', cpu.percent);
  check('memory', 'RAM', memory.percent);
  check('disk', 'Disco', disk.percent);
  return alerts;
}

let vmCache = { at: 0, available: 0 };

/**
 * os.freemem() on macOS only reports truly free pages, so a healthy Mac looks
 * like it is at 99% RAM. Use vm_stat and count inactive/speculative pages as
 * reclaimable, which is what Activity Monitor calls "available".
 */
function memoryAvailable() {
  const total = os.totalmem();
  if (process.platform !== 'darwin') return { total, available: os.freemem() };
  if (Date.now() - vmCache.at < 5000 && vmCache.available > 0) {
    return { total, available: vmCache.available };
  }
  try {
    const out = execFileSync('vm_stat', { encoding: 'utf8' });
    const pageSize = Number(out.match(/page size of (\d+) bytes/)?.[1]) || 4096;
    const pages = (label) => {
      const match = out.match(new RegExp(`${label}:\\s+(\\d+)`));
      return match ? Number(match[1]) : 0;
    };
    const available =
      (pages('Pages free') + pages('Pages inactive') + pages('Pages speculative')) *
      pageSize;
    vmCache = { at: Date.now(), available };
    return { total, available };
  } catch {
    return { total, available: os.freemem() };
  }
}

export function systemSnapshot() {
  purgeClients();
  const cpus = os.cpus();
  const cores = cpus.length || 1;
  const load1 = safeNumber(os.loadavg()[0]);
  const cpuPercent = Math.min(100, Math.round((load1 / cores) * 1000) / 10);

  const { total: memoryTotal, available: memoryFree } = memoryAvailable();
  const memoryUsed = Math.max(0, memoryTotal - memoryFree);
  const memory = {
    total: memoryTotal,
    free: memoryFree,
    used: memoryUsed,
    percent: percent(memoryUsed, memoryTotal),
  };
  const disk = diskUsage();
  const cpu = { cores, load1, percent: cpuPercent };
  const processInfo = process.memoryUsage();
  const alerts = buildAlerts({ cpu, memory, disk });
  const health = alerts.some((a) => a.level === SEVERITY.critical)
    ? SEVERITY.critical
    : alerts.length
      ? SEVERITY.warning
      : SEVERITY.ok;

  const settings = getAllSettings();

  const lastCatalog = safeNumber(getSetting('last_ingest', '0')) || null;
  const lastEpg = lastEpgFetch();
  const stats = getStats();
  const devices = listClients();

  return {
    ok: true,
    name: getSetting('server_name', config.serverName),
    version: config.version,
    serverTime: Math.floor(Date.now() / 1000),
    startedAt: Math.floor(startedAt / 1000),
    uptimeSeconds: Math.floor((Date.now() - startedAt) / 1000),
    health,
    alerts,
    resources: {
      cpu,
      memory,
      disk,
      process: {
        rss: processInfo.rss,
        heapUsed: processInfo.heapUsed,
        heapTotal: processInfo.heapTotal,
      },
    },
    lastUpdate: {
      catalog: lastCatalog,
      epg: lastEpg,
      ingest: settings.last_ingest ? safeNumber(settings.last_ingest) : lastCatalog,
    },
    stats,
    devices,
    deviceCount: devices.length,
  };
}

export function versionInfo(clientVersion = '') {
  const latest = clean(process.env.LATEST_APP_VERSION, 32) || config.latestAppVersion || '1.6.0';
  const current = clean(clientVersion, 32);
  const compare = (a, b) => {
    const pa = a.split('.').map((n) => safeNumber(n));
    const pb = b.split('.').map((n) => safeNumber(n));
    for (let i = 0; i < Math.max(pa.length, pb.length, 3); i += 1) {
      const diff = (pa[i] || 0) - (pb[i] || 0);
      if (diff !== 0) return diff;
    }
    return 0;
  };
  return {
    latest,
    current: current || null,
    updateAvailable: Boolean(current) && compare(latest, current) > 0,
    downloadUrl: clean(process.env.APP_DOWNLOAD_URL, 500) || '/api/update/casatv-tv.apk',
    changelog: clean(process.env.APP_CHANGELOG, 500) || null,
    minVersion: clean(process.env.MIN_APP_VERSION, 32) || null,
  };
}

export function resetSystemStateForTests() {
  clients.clear();
}
