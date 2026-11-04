// Captures d'écran minimales, sans suite Playwright ni serveur de dev (ils font planter
// WSL) : un seul Chromium headless, une page à la fois.
//   timeout 240 node docs/decisions/mockups/shoot.mjs [mockups|prod] [dossier de sortie]
// « mockups » capture chaque écran maquetté (directions A et B) ; « prod » capture les
// pages publiques de https://ageguessr.app/fr en desktop et mobile.
import { createRequire } from 'node:module';
import { mkdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const here = new URL('.', import.meta.url).pathname;
const require = createRequire(join(here, '../../../frontend/package.json'));
const { chromium } = require('@playwright/test');

const mode = process.argv[2] ?? 'mockups';
const out = process.argv[3] ?? join(tmpdir(), 'ageguessr-shots');
mkdirSync(out, { recursive: true });

const browser = await chromium.launch({ headless: true });
try {
  if (mode === 'mockups') {
    const page = await browser.newPage({ viewport: { width: 1800, height: 1000 } });
    for (const d of ['a', 'b']) {
      await page.goto(`file://${join(here, 'index.html')}?d=${d}`, { waitUntil: 'networkidle' });
      await page.evaluate(() => document.fonts.ready);
      for (const frame of await page.locator('[data-screen]').all()) {
        await frame.screenshot({ path: join(out, `${await frame.getAttribute('data-screen')}.png`) });
      }
    }
  } else {
    const viewports = { desktop: { width: 1440, height: 900 }, mobile: { width: 390, height: 844 } };
    for (const [vp, viewport] of Object.entries(viewports)) {
      const page = await browser.newPage({ viewport });
      for (const path of ['/', '/free', '/login', '/register']) {
        await page.goto(`https://ageguessr.app/fr${path}`, { waitUntil: 'networkidle' });
        await page.screenshot({ path: join(out, `prod-${vp}${path.replace(/\//g, '-') || '-home'}.png`), fullPage: true });
      }
      await page.close();
    }
  }
} finally {
  await browser.close();
}
console.log(`Captures dans ${out}`);
