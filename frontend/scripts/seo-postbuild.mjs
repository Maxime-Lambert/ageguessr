// Writes what the link previews read into each language's index.html, and robots.txt and
// sitemap.xml at the root of the build. Run after `ng build` (package.json "build").
//
// Why after the build: Angular does not translate index.html, and the previews (WhatsApp,
// Discord, X…) never run the JavaScript that sets the tags at run time (features/seo).
// The texts come from the XLIFF files, under the ids of src/app/features/seo/seo-texts.ts:
// one source for both. See docs/decisions/architecture.md, « Référencement ».
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

/** The production site. Not a secret: it is in every shared link. */
export const SITE = 'https://ageguessr.app';
export const LANGUAGES = [
  { code: 'en', ogLocale: 'en_US' },
  { code: 'fr', ogLocale: 'fr_FR' },
];
/**
 * The pages worth indexing, as in app.routes.ts (the ones without `noindex`). Keep both
 * lists in step.
 */
export const INDEXED_PATHS = ['', 'free', 'how-to-play', 'legal-notice', 'terms', 'privacy'];
export const OG_IMAGE = { file: 'og-image.png', width: 1200, height: 630 };
const IDS = ['seo.title', 'seo.description'];

const XML_ENTITIES = { '&amp;': '&', '&lt;': '<', '&gt;': '>', '&quot;': '"', '&apos;': "'" };

/** The text of each id, from the target when the file has one (a translation), else the source. */
export function readTranslations(xliff, ids = IDS) {
  const texts = {};
  for (const id of ids) {
    const unit = xliff.match(
      new RegExp(`<trans-unit id="${id.replaceAll('.', '\\.')}"[^>]*>([\\s\\S]*?)</trans-unit>`),
    );
    if (!unit) {
      throw new Error(`No trans-unit "${id}": see src/app/features/seo/seo-texts.ts`);
    }
    const match =
      unit[1].match(/<target>([\s\S]*?)<\/target>/) ??
      unit[1].match(/<source>([\s\S]*?)<\/source>/);
    if (!match) {
      throw new Error(`"${id}" has no <source> nor <target>`);
    }
    const text = match[1];
    if (text.includes('<')) {
      throw new Error(`"${id}" holds markup or a placeholder, which index.html cannot take`);
    }
    texts[id] = text.replace(/&(amp|lt|gt|quot|apos);/g, (entity) => XML_ENTITIES[entity]);
  }
  return texts;
}

export function escapeHtml(text) {
  return text
    .replaceAll('&', '&amp;')
    .replaceAll('"', '&quot;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;');
}

/** The tags of one language, between markers so that a second run replaces them. */
export function metaTags(texts, language) {
  const title = escapeHtml(texts['seo.title']);
  const description = escapeHtml(texts['seo.description']);
  const url = `${SITE}/${language.code}/`;
  const others = LANGUAGES.filter((other) => other.code !== language.code);
  return [
    '<!-- seo -->',
    `<meta name="description" content="${description}" />`,
    '<meta property="og:type" content="website" />',
    '<meta property="og:site_name" content="AgeGuessr" />',
    `<meta property="og:title" content="${title}" />`,
    `<meta property="og:description" content="${description}" />`,
    `<meta property="og:url" content="${url}" />`,
    `<meta property="og:image" content="${url}${OG_IMAGE.file}" />`,
    `<meta property="og:image:width" content="${OG_IMAGE.width}" />`,
    `<meta property="og:image:height" content="${OG_IMAGE.height}" />`,
    `<meta property="og:locale" content="${language.ogLocale}" />`,
    ...others.map((other) => `<meta property="og:locale:alternate" content="${other.ogLocale}" />`),
    '<meta name="twitter:card" content="summary_large_image" />',
    '<!-- /seo -->',
  ].join('\n    ');
}

/** index.html with the tags of its language and the preview's title. */
export function injectMeta(html, texts, language) {
  if (!html.includes('</head>')) {
    throw new Error('index.html has no </head>');
  }
  return html
    .replace(/\s*<!-- seo -->[\s\S]*?<!-- \/seo -->/, '')
    .replace(/<title>[\s\S]*?<\/title>/, `<title>${escapeHtml(texts['seo.title'])}</title>`)
    .replace('</head>', `  ${metaTags(texts, language)}\n  </head>`);
}

/** Every indexed page in every language, each listing its versions (hreflang). */
export function sitemapXml() {
  const alternates = (path) =>
    [
      ...LANGUAGES.map(
        ({ code }) =>
          `    <xhtml:link rel="alternate" hreflang="${code}" href="${SITE}/${code}/${path}" />`,
      ),
      `    <xhtml:link rel="alternate" hreflang="x-default" href="${SITE}/${path}" />`,
    ].join('\n');
  const urls = INDEXED_PATHS.flatMap((path) =>
    LANGUAGES.map(
      ({ code }) =>
        `  <url>\n    <loc>${SITE}/${code}/${path}</loc>\n${alternates(path)}\n  </url>`,
    ),
  );
  return [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">',
    ...urls,
    '</urlset>',
    '',
  ].join('\n');
}

/**
 * Everything may be crawled but the API and the admin pages. The private pages are
 * crawlable on purpose: their noindex is only seen by a crawler allowed to read them.
 */
export function robotsTxt() {
  return [
    'User-agent: *',
    'Disallow: /api/',
    ...LANGUAGES.map(({ code }) => `Disallow: /${code}/admin`),
    '',
    `Sitemap: ${SITE}/sitemap.xml`,
    '',
  ].join('\n');
}

function main() {
  const frontend = join(dirname(fileURLToPath(import.meta.url)), '..');
  const browser = join(frontend, 'dist/frontend/browser');
  const locale = join(frontend, 'src/locale');
  const xliff = {
    en: readFileSync(join(locale, 'messages.xlf'), 'utf8'),
    fr: readFileSync(join(locale, 'messages.fr.xlf'), 'utf8'),
  };
  for (const language of LANGUAGES) {
    const index = join(browser, language.code, 'index.html');
    const texts = readTranslations(xliff[language.code]);
    writeFileSync(index, injectMeta(readFileSync(index, 'utf8'), texts, language));
  }
  writeFileSync(join(browser, 'robots.txt'), robotsTxt());
  writeFileSync(join(browser, 'sitemap.xml'), sitemapXml());
  console.log('SEO: index.html (en, fr), robots.txt and sitemap.xml written');
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main();
}
