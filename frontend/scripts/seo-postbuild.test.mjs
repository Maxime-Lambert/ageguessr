// node:test, not Vitest: this script runs on Node after the build, outside Angular.
// Run by `pnpm test` (node --test scripts/).
import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  INDEXED_PATHS,
  LANGUAGES,
  injectMeta,
  readTranslations,
  robotsTxt,
  sitemapXml,
} from './seo-postbuild.mjs';

const [EN, FR] = LANGUAGES;
const unit = (id, inner) => `<trans-unit id="${id}" datatype="html">${inner}</trans-unit>`;
const SOURCE = [
  unit('seo.title', '<source>Guess &amp; win</source><context-group/>'),
  unit('seo.titleOther', '<source>Not this one</source>'),
  unit('seo.description', '<source>A "daily" game</source>'),
].join('\n');
const INDEX = '<html lang="fr">\n  <head>\n    <title>AgeGuessr</title>\n  </head>\n</html>';
const TEXTS = { 'seo.title': 'Devinez l’âge', 'seo.description': 'Un jeu "quotidien" <3' };

test('reads the source of each id, its XML entities decoded', () => {
  assert.deepEqual(readTranslations(SOURCE), {
    'seo.title': 'Guess & win',
    'seo.description': 'A "daily" game',
  });
});

test('prefers the target, the translation', () => {
  const french =
    unit('seo.title', '<source>Guess</source><target>Devinez</target>') +
    unit('seo.description', '<source>Game</source><target>Jeu</target>');
  assert.equal(readTranslations(french)['seo.title'], 'Devinez');
});

test('fails loudly on a missing id or a placeholder', () => {
  assert.throws(
    () => readTranslations(unit('seo.title', '<source>x</source>')),
    /seo\.description/,
  );
  const placeholder =
    unit('seo.title', '<source>Hi <x id="name"/></source>') +
    unit('seo.description', '<source>x</source>');
  assert.throws(() => readTranslations(placeholder), /placeholder/);
});

test('writes the tags of its language once, escaped, before </head>, with the title', () => {
  const html = injectMeta(INDEX, TEXTS, FR);

  assert.match(html, /<title>Devinez l’âge<\/title>/);
  assert.match(html, /<meta name="description" content="Un jeu &quot;quotidien&quot; &lt;3" \/>/);
  assert.match(html, /<meta property="og:locale" content="fr_FR" \/>/);
  assert.match(html, /<meta property="og:locale:alternate" content="en_US" \/>/);
  assert.match(html, /<meta property="og:url" content="https:\/\/ageguessr\.app\/fr\/" \/>/);
  assert.match(html, /content="https:\/\/ageguessr\.app\/fr\/og-image\.png"/);
  assert.match(html, /twitter:card" content="summary_large_image"/);
  assert.ok(html.indexOf('<!-- /seo -->') < html.indexOf('</head>'));
});

test('replaces its tags when run twice', () => {
  const twice = injectMeta(injectMeta(INDEX, TEXTS, EN), TEXTS, EN);
  assert.equal(twice.match(/og:title/g).length, 1);
  assert.equal(twice, injectMeta(INDEX, TEXTS, EN));
});

test('lists every indexed page in both languages, each with its versions', () => {
  const xml = sitemapXml();
  assert.equal(xml.match(/<url>/g).length, INDEXED_PATHS.length * LANGUAGES.length);
  assert.match(
    xml,
    /<loc>https:\/\/ageguessr\.app\/fr\/how-to-play<\/loc>\n {4}<xhtml:link rel="alternate" hreflang="en" href="https:\/\/ageguessr\.app\/en\/how-to-play" \/>/,
  );
  assert.match(xml, /hreflang="x-default" href="https:\/\/ageguessr\.app\/" \/>/);
  assert.doesNotMatch(xml, /account|login|admin|challenge/);
});

test('lets everything be crawled but the API and the admin, and names the sitemap', () => {
  assert.equal(
    robotsTxt(),
    'User-agent: *\nDisallow: /api/\nDisallow: /en/admin\nDisallow: /fr/admin\n\nSitemap: https://ageguessr.app/sitemap.xml\n',
  );
});
