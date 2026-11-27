import Fastify from 'fastify';
import fastifyStatic from '@fastify/static';
import { Bonjour } from 'bonjour-service';
import os from 'node:os';
import { config } from './config.js';
import { getStats, getSetting } from './db.js';
import { registerApi } from './api.js';
import { ingestState, needsRefresh, runIngest } from './catalog.js';
import { autoRefreshEpg, seedDefaultEpg } from './epg.js';
import { cleanLogoCache } from './logos.js';
import { startScanScheduler } from './scan.js';

const app = Fastify({
  logger: {
    level: process.env.LOG_LEVEL || 'warn',
    base: undefined,
  },
  disableRequestLogging: true,
  bodyLimit: 2 * 1024 * 1024,
  maxParamLength: 4096,
});

app.addHook('onSend', async (req, reply, payload) => {
  reply.header('access-control-allow-origin', '*');
  reply.header(
    'access-control-allow-headers',
    'content-type,range,x-profile-id,x-device-id,x-device-name,x-device-platform,x-app-version,x-playback-state,x-playback-channel',
  );
  reply.header('access-control-allow-methods', 'GET,POST,PATCH,DELETE,OPTIONS');
  reply.header('access-control-expose-headers', 'content-length,content-range,accept-ranges');
  reply.header('x-content-type-options', 'nosniff');
  return payload;
});

app.addHook('onRequest', async (req, reply) => {
  if (req.method === 'OPTIONS') {
    reply.code(204).send();
  }
});

app.setErrorHandler((error, req, reply) => {
  req.log.error(error);
  const status = Number(error.statusCode) || 500;
  if (status >= 500) {
    reply.code(500).send({ error: 'Errore interno del server' });
  } else {
    reply.code(status).send({ error: error.message });
  }
});

app.setNotFoundHandler((req, reply) => {
  if (req.url.startsWith('/api/')) {
    reply.code(404).send({ error: 'Endpoint non trovato' });
    return;
  }
  reply.code(404).type('text/plain').send('Not found');
});

await registerApi(app);

await app.register(fastifyStatic, {
  root: config.publicDir,
  prefix: '/',
  index: ['index.html'],
  cacheControl: true,
  maxAge: '1h',
  setHeaders(res, filePath) {
    if (filePath.endsWith('index.html')) {
      res.setHeader('cache-control', 'no-cache');
    }
    if (filePath.includes('hls.min.js')) {
      res.setHeader('cache-control', 'public, max-age=604800, immutable');
    }
  },
});

function lanAddresses() {
  const out = [];
  for (const [name, addresses] of Object.entries(os.networkInterfaces())) {
    for (const address of addresses || []) {
      if (address.family === 'IPv4' && !address.internal) {
        out.push({ name, address: address.address });
      }
    }
  }
  return out;
}

async function start() {
  const stats = getStats();
  if (stats.allChannels === 0 && config.sourceIndexUrl) {
    console.log('[startup] primo avvio: scarico la playlist configurata...');
    try {
      await runIngest({ force: true, reason: 'first-run' });
    } catch (error) {
      console.error('[startup] catalogo non disponibile:', error.message);
    }
  } else if (stats.allChannels === 0) {
    console.log('[startup] nessuna playlist configurata. Inserisci una playlist M3U dal pannello web o dall\'app TV.');
  }

  await app.listen({ port: config.port, host: config.host });
  console.log('');
  console.log(`  ${getSetting('server_name', config.serverName)} ${config.version}`);
  console.log(`  Locale:   http://localhost:${config.port}`);
  for (const { name, address } of lanAddresses()) {
    console.log(`  Rete:     http://${address}:${config.port}  (${name})`);
  }
  console.log('');

  let bonjour = null;
  try {
    bonjour = new Bonjour();
    bonjour.publish({
      name: getSetting('server_name', config.serverName),
      type: 'casatv',
      port: config.port,
      txt: { path: '/', version: config.version, api: 'v1' },
    });
    console.log('[discovery] annuncio mDNS attivo (_casatv._tcp)');
  } catch (error) {
    console.warn('[discovery] mDNS non disponibile:', error.message);
  }

  seedDefaultEpg();
  autoRefreshEpg();
  startScanScheduler();

  if (getStats().allChannels > 0 && needsRefresh() && !ingestState.running) {
    setTimeout(() => {
      runIngest({ reason: 'startup' }).catch((error) =>
        console.error('[catalog] aggiornamento automatico fallito:', error.message),
      );
    }, 5000);
  }

  // Periodic automatic catalog refresh every 6 hours (checked every 30 minutes)
  setInterval(
    () => {
      if (needsRefresh() && !ingestState.running) {
        runIngest({ reason: 'scheduled' }).catch((error) =>
          console.error('[catalog] aggiornamento programmato fallito:', error.message),
        );
      }
    },
    30 * 60 * 1000,
  ).unref();

  cleanLogoCache().catch(() => {});
  setInterval(() => cleanLogoCache().catch(() => {}), 24 * 3600 * 1000);

  const shutdown = async (signal) => {
    console.log(`\n[stop] ${signal}`);
    try {
      bonjour?.destroy();
    } catch {
      // ignore
    }
    await app.close().catch(() => {});
    process.exit(0);
  };
  process.on('SIGINT', () => shutdown('SIGINT'));
  process.on('SIGTERM', () => shutdown('SIGTERM'));
}

process.on('unhandledRejection', (error) => {
  console.error('[unhandledRejection]', error);
});

start().catch((error) => {
  console.error('[startup] errore fatale:', error);
  process.exit(1);
});
