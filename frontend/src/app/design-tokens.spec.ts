import styles from '../styles.css' with { loader: 'text' };

// Guards the WCAG AA contrast of the color tokens in both themes (see "Accessibilité des
// couleurs" in docs/decisions/design.md). The tokens are read from styles.css itself, so a
// color tweak that breaks a pair fails here instead of silently shipping.

type Tokens = Record<string, string>;

const TEXT = 4.5; // WCAG 1.4.3, normal-size text
const NON_TEXT = 3; // WCAG 1.4.11, form field boundaries and graphical indicators

// Assumes a flat block (custom properties only): `[^}]*` stops at the first closing brace,
// so a nested rule inside @theme or .light would silently cut the token list short.
function readBlock(selector: string): Tokens {
  const block = new RegExp(`${selector}\\s*\\{([^}]*)\\}`).exec(styles)?.[1];
  if (!block) throw new Error(`No "${selector}" block in styles.css`);
  const tokens: Tokens = {};
  for (const [, name, value] of block.matchAll(/--color-([\w-]+):\s*(#[0-9a-f]{6})/gi)) {
    tokens[name] = value;
  }
  return tokens;
}

function luminance(hex: string): number {
  const [r, g, b] = [1, 3, 5].map((i) => {
    const c = parseInt(hex.slice(i, i + 2), 16) / 255;
    return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

// [foreground token, background token, minimum ratio]
const PAIRS: [string, string, number][] = [
  ...['foreground', 'muted', 'accent', 'danger'].flatMap((fg) =>
    ['background', 'surface'].map((bg): [string, string, number] => [fg, bg, TEXT]),
  ),
  ['accent-foreground', 'accent', TEXT],
  ['danger-foreground', 'danger', TEXT],
  ['hot-foreground', 'hot', TEXT],
  ['cold-foreground', 'cold', TEXT],
  ...['input-border', 'hot', 'cold'].flatMap((fg) =>
    ['background', 'surface'].map((bg): [string, string, number] => [fg, bg, NON_TEXT]),
  ),
];

const dark = readBlock('@theme');
const THEMES: Record<string, Tokens> = {
  dark,
  // .light only overrides some tokens, the others are inherited from @theme.
  light: { ...dark, ...readBlock('\\.light') },
};

describe('design tokens contrast', () => {
  for (const [theme, tokens] of Object.entries(THEMES)) {
    describe(theme, () => {
      it.each(PAIRS)('%s on %s reaches %s:1', (fg, bg, min) => {
        expect(tokens[fg], `--color-${fg} is missing`).toBeDefined();
        expect(tokens[bg], `--color-${bg} is missing`).toBeDefined();
        expect(contrast(tokens[fg], tokens[bg])).toBeGreaterThanOrEqual(min);
      });
    });
  }
});
