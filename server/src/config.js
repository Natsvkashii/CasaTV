import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
export const ROOT = path.resolve(__dirname, '..');

// Optional .env (Node 22+). Never required.
try {
  const envFile = path.join(ROOT, '.env');
  if (fs.existsSync(envFile)) process.loadEnvFile(envFile);
} catch {
  // ignore malformed/missing .env
}

function int(value, fallback) {
  const n = Number.parseInt(value ?? '', 10);
  return Number.isFinite(n) ? n : fallback;
}

export const config = {
  root: ROOT,
  dataDir: path.resolve(process.env.DATA_DIR || path.join(ROOT, 'data')),
  publicDir: path.join(ROOT, 'public'),
  port: int(process.env.PORT, 8787),
  host: process.env.HOST || '0.0.0.0',
  publicUrl: (process.env.PUBLIC_URL || '').replace(/\/+$/, ''),
  serverName: process.env.SERVER_NAME || 'CasaTV',
  defaultCountry: (process.env.DEFAULT_COUNTRY || 'IT').toUpperCase(),
  sourceIndexUrl: process.env.SOURCE_INDEX_URL || '',
  apiBase: process.env.IPTV_API_BASE || 'https://iptv-org.github.io/api',
  refreshHours: int(process.env.REFRESH_HOURS, 6),
  defaultEpgUrl:
    process.env.DEFAULT_EPG_URL ||
    'https://epgshare01.online/epgshare01/epg_ripper_IT1.xml.gz',
  userAgent:
    process.env.DEFAULT_USER_AGENT ||
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36',
  version: '1.6.0',
  latestAppVersion: process.env.LATEST_APP_VERSION || '1.6.0',
};

fs.mkdirSync(config.dataDir, { recursive: true });

export const paths = {
  db: path.join(config.dataDir, 'casatv.db'),
  cache: path.join(config.dataDir, 'cache'),
  logos: path.join(config.dataDir, 'logo-cache'),
};

fs.mkdirSync(paths.cache, { recursive: true });
fs.mkdirSync(paths.logos, { recursive: true });
