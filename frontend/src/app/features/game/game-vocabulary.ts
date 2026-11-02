import { Direction, HintCategory, MAX_AGE, MIN_AGE } from './models';

/**
 * Every word of the game screens comes from here, never from a template
 * (docs/decisions/design.md, « Voix dossier d'enquête »). The investigation voice is on
 * trial: if a first playtest shows a player hesitating on "case" or "lead", switch
 * VOICE to NEUTRAL_VOICE, without touching any screen.
 */
export const INVESTIGATION_VOICE = {
  round: 'Case',
  dailyGame: "Today's file",
  hint: 'Lead',
  pickHint: 'Pick a lead',
  noHint: 'No lead turned up for this case. Make your final guess.',
  result: 'Case closed',
  summary: 'Case report',
  next: 'Next case',
  seeSummary: 'See the case report',
  removed: 'Withdrawn from the file',
};

export const NEUTRAL_VOICE: typeof INVESTIGATION_VOICE = {
  round: 'Round',
  dailyGame: 'Daily game',
  hint: 'Hint',
  pickHint: 'Pick a hint',
  noHint: 'No hint for this round. Make your final guess.',
  result: 'Result',
  summary: 'Summary',
  next: 'Next round',
  seeSummary: 'See the summary',
  removed: 'Photo removed',
};

const VOICE = INVESTIGATION_VOICE;

export const GAME_TEXT = {
  ...VOICE,
  question: 'How old were they on this photo?',
  photoAlt: 'The person to guess',
  guessLabel: 'Age',
  firstGuessButton: 'Guess',
  secondGuessButton: 'Final guess',
  yourGuess: 'Your guess',
  firstGuess: 'First guess',
  secondGuess: 'Final guess',
  realAge: 'Age on the photo',
  exact: 'Spot on, first try.',
  points: 'points',
  outOf: 'out of',
  hintFrom: 'From',
  photoBy: 'Photo',
  unknownAuthor: 'Unknown author',
  source: 'Commons page',
  wikipedia: 'Wikipedia',
  hintLicense: 'CC BY-SA',
  loading: 'Opening the file…',
  noGame: 'No case today. Come back tomorrow.',
  retry: 'Try again',
  invalidAge: `Enter a whole age from ${MIN_AGE} to ${MAX_AGE}.`,
  imageFailed: 'The photo could not load.',
  removedExplained: 'This photo was withdrawn for everyone. It does not count in your score.',
  closed: "Today's file is closed. Here is the new one.",
  genericError: 'Something went wrong. Please try again.',
  comeBack: 'A new file opens at midnight UTC.',
  hintLanguage: 'Hints in',
} as const;

/** The side only, never how far: no "warm" or "cold" (« Déroulé d'une manche »). */
export function directionText(direction: Direction, guess: number): string {
  return direction === 'OLDER' ? `Older than ${guess}` : `Younger than ${guess}`;
}

export const CATEGORY_LABELS: Record<HintCategory, string> = {
  MUSIC: 'Music',
  FILM: 'Film',
  SPORT: 'Sport',
  SCIENCE: 'Science',
  WORLD: 'World events',
  CELEBRITIES: 'Celebrities',
};

export function roundTitle(position: number, total: number): string {
  return `${GAME_TEXT.round} ${position} of ${total}`;
}
