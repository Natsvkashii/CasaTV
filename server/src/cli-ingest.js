import { runIngest } from './catalog.js';
import { refreshAllSources, seedDefaultEpg } from './epg.js';

const withEpg = process.argv.includes('--epg');

try {
  const result = await runIngest({ force: true, reason: 'cli' });
  console.log(JSON.stringify(result, null, 2));
  if (withEpg) {
    seedDefaultEpg();
    const epg = await refreshAllSources({ force: true });
    console.log(JSON.stringify(epg, null, 2));
  }
} catch (error) {
  console.error(error);
  process.exit(1);
}
