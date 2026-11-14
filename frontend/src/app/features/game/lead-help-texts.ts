import { HintCategory } from './models';

/** The explanation of one lead card: what kind of fact, the rule, a worked example. */
export interface LeadHelp {
  kind: string;
  rule: string;
  example: string;
}

/**
 * The common rule: the fact dates from as many years before the game as the age on the
 * photo (the API picks it at game year − age). Celebrities say it their own way: the
 * famous person is, this year, as old as the person on the photo.
 */
const RULE = $localize`:@@game.leadHelp.rule:The fact dates from as many years ago as the person's age on the photo.`;
const CELEBRITIES_RULE = $localize`:@@game.leadHelp.celebrities.rule:This famous person is as old this year as the person on the photo.`;

/** One real fact per category, with its year: the gap is computed, never written down. */
const EXAMPLES: Record<HintCategory, { kind: string; hint: string; year: number }> = {
  MUSIC: {
    kind: $localize`:@@game.leadHelp.music.kind:An album, a song, a Eurovision or a Grammy win.`,
    hint: $localize`:@@game.leadHelp.music.hint:Album "Thriller" — Michael Jackson`,
    year: 1982,
  },
  FILM: {
    kind: $localize`:@@game.leadHelp.film.kind:A film released, or an Oscar, Palme d'or or César.`,
    hint: $localize`:@@game.leadHelp.film.hint:Release of the film "Titanic"`,
    year: 1997,
  },
  SPORT: {
    kind: $localize`:@@game.leadHelp.sport.kind:A competition and its winner, a Ballon d'Or, the Olympics.`,
    hint: $localize`:@@game.leadHelp.sport.hint:World Cup, host: France — winner: France`,
    year: 1998,
  },
  SCIENCE: {
    kind: $localize`:@@game.leadHelp.science.kind:A discovery or invention, a Nobel, a launch, a first flight.`,
    hint: $localize`:@@game.leadHelp.science.hint:First flight: Concorde`,
    year: 1969,
  },
  WORLD: {
    kind: $localize`:@@game.leadHelp.world.kind:An event, a treaty, an election, a new state.`,
    hint: $localize`:@@game.leadHelp.world.hint:Fall of the Berlin Wall`,
    year: 1989,
  },
  LITERATURE: {
    kind: $localize`:@@game.leadHelp.literature.kind:A work published, or a literary prize.`,
    hint: $localize`:@@game.leadHelp.literature.hint:Published: "Harry Potter and the Philosopher's Stone" — J. K. Rowling`,
    year: 1997,
  },
  CELEBRITIES: {
    kind: $localize`:@@game.leadHelp.celebrities.kind:A famous person, as old this year as the person on the photo.`,
    hint: 'Tom Hanks',
    year: 1956,
  },
};

/** The help of a lead card, its example counted from the year of the game played. */
export function leadHelp(category: HintCategory, gameYear: number): LeadHelp {
  const { kind, hint, year } = EXAMPLES[category];
  const years = gameYear - year;
  if (category === 'CELEBRITIES') {
    return {
      kind,
      rule: CELEBRITIES_RULE,
      example: $localize`:@@game.leadHelp.celebrities.example:${hint}:name:, born in ${year}:year:: ${years}:years: this year → ${years}:age: on the photo.`,
    };
  }
  return {
    kind,
    rule: RULE,
    example: $localize`:@@game.leadHelp.example:${hint}:hint:: ${years}:years: years ago → ${years}:age: on the photo.`,
  };
}

/** The name of the ⓘ button: which card it explains, the category's label given. */
export function leadHelpLabel(label: string): string {
  return $localize`:@@game.leadHelp.button:How the ${label}:category: hint works`;
}
