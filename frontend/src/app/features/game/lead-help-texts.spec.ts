import { leadHelp } from './lead-help-texts';
import { HintCategory } from './models';

const CATEGORIES: HintCategory[] = [
  'MUSIC',
  'FILM',
  'SPORT',
  'SCIENCE',
  'WORLD',
  'LITERATURE',
  'CELEBRITIES',
];

describe('lead help texts', () => {
  it('explains every category: its kind of fact, the rule, an example', () => {
    CATEGORIES.forEach((category) => {
      const help = leadHelp(category, 2026);
      expect(help.kind.trim(), category).not.toBe('');
      expect(help.rule.trim(), category).not.toBe('');
      expect(help.example.trim(), category).not.toBe('');
    });
  });

  it('gives each category its own kind of fact', () => {
    const kinds = CATEGORIES.map((category) => leadHelp(category, 2026).kind);
    expect(new Set(kinds).size).toBe(CATEGORIES.length);
  });

  it('counts the example from the year of the game, so it never goes stale', () => {
    expect(leadHelp('FILM', 2026).example).toBe(
      'Release of the film "Titanic": 29 years ago → 29 on the photo.',
    );
    expect(leadHelp('FILM', 2030).example).toBe(
      'Release of the film "Titanic": 33 years ago → 33 on the photo.',
    );
  });

  it('tells celebrities by their age this year, with their own rule', () => {
    const help = leadHelp('CELEBRITIES', 2026);
    expect(help.example).toBe('Tom Hanks, born in 1956: 70 this year → 70 on the photo.');
    expect(help.rule).not.toBe(leadHelp('FILM', 2026).rule);
  });

  it('shares one rule between every category but celebrities', () => {
    const rules = CATEGORIES.filter((category) => category !== 'CELEBRITIES').map(
      (category) => leadHelp(category, 2026).rule,
    );
    expect(new Set(rules).size).toBe(1);
  });
});
