import { COMMON_MESSAGES } from '../../shared/common-messages';
import { Direction, HintCategory, MAX_AGE, MIN_AGE } from './models';

/**
 * Every word of the game screens comes from here, never from a template
 * (docs/decisions/design.md, « Voix dossier d'enquête »), translated with Angular i18n
 * ($localize, src/locale/messages.fr.xlf). The investigation voice is on trial: if a
 * first playtest shows a player hesitating on "case" or "lead", switch VOICE to
 * NEUTRAL_VOICE, without touching any screen. Both voices exist in both languages.
 */
export const INVESTIGATION_VOICE = {
  round: $localize`:@@game.voice.round:Case`,
  dailyGame: $localize`:@@game.voice.dailyGame:Today's file`,
  hint: $localize`:@@game.voice.hint:Lead`,
  pickHint: $localize`:@@game.voice.pickHint:Pick a lead`,
  noHint: $localize`:@@game.voice.noHint:No lead turned up for this case. Make your final guess.`,
  result: $localize`:@@game.voice.result:Case closed`,
  summary: $localize`:@@game.voice.summary:Case report`,
  next: $localize`:@@game.voice.next:Next case`,
  seeSummary: $localize`:@@game.voice.seeSummary:See the case report`,
  removed: $localize`:@@game.voice.removed:Withdrawn from the file`,
};

export const NEUTRAL_VOICE: typeof INVESTIGATION_VOICE = {
  round: $localize`:@@game.neutral.round:Round`,
  dailyGame: $localize`:@@game.neutral.dailyGame:Daily game`,
  hint: $localize`:@@game.neutral.hint:Hint`,
  pickHint: $localize`:@@game.neutral.pickHint:Pick a hint`,
  noHint: $localize`:@@game.neutral.noHint:No hint for this round. Make your final guess.`,
  result: $localize`:@@game.neutral.result:Result`,
  summary: $localize`:@@game.neutral.summary:Summary`,
  next: $localize`:@@game.neutral.next:Next round`,
  seeSummary: $localize`:@@game.neutral.seeSummary:See the summary`,
  removed: $localize`:@@game.neutral.removed:Photo removed`,
};

const VOICE = INVESTIGATION_VOICE;

export const GAME_TEXT = {
  ...VOICE,
  question: $localize`:@@game.question:How old were they on this photo?`,
  photoAlt: $localize`:@@game.photoAlt:The person to guess`,
  guessLabel: $localize`:@@game.guessLabel:Age`,
  firstGuessButton: $localize`:@@game.firstGuessButton:Guess`,
  secondGuessButton: $localize`:@@game.secondGuessButton:Final guess`,
  yourGuess: $localize`:@@game.yourGuess:Your guess`,
  firstGuess: $localize`:@@game.firstGuess:First guess`,
  secondGuess: $localize`:@@game.secondGuess:Final guess`,
  realAge: $localize`:@@game.realAge:Age on the photo`,
  exact: $localize`:@@game.exact:Spot on, first try.`,
  points: $localize`:@@game.points:points`,
  outOf: $localize`:@@game.outOf:out of`,
  hintFrom: $localize`:@@game.hintFrom:From`,
  wikipedia: 'Wikipedia',
  hintLicense: 'CC BY-SA',
  photoBy: $localize`:@@game.photoBy:Photo`,
  unknownAuthor: $localize`:@@game.unknownAuthor:Unknown author`,
  source: $localize`:@@game.source:Commons page`,
  loading: $localize`:@@game.loading:Opening the file…`,
  noGame: $localize`:@@game.noGame:No case today. Come back tomorrow.`,
  retry: $localize`:@@game.retry:Try again`,
  invalidAge: $localize`:@@game.invalidAge:Enter a whole age from ${MIN_AGE}:min: to ${MAX_AGE}:max:.`,
  imageFailed: $localize`:@@game.imageFailed:The photo could not load.`,
  removedExplained: $localize`:@@game.removedExplained:This photo was withdrawn for everyone. It does not count in your score.`,
  closed: $localize`:@@game.closed:Today's file is closed. Here is the new one.`,
  genericError: COMMON_MESSAGES.genericError,
  comeBack: $localize`:@@game.comeBack:A new file opens at midnight UTC.`,
  hintLanguage: $localize`:@@game.hintLanguage:Hints in`,
};

/** The side only, never how far: no "warm" or "cold" (« Déroulé d'une manche »). */
export function directionText(direction: Direction, guess: number): string {
  return direction === 'OLDER'
    ? $localize`:@@game.older:Older than ${guess}:guess:`
    : $localize`:@@game.younger:Younger than ${guess}:guess:`;
}

export const CATEGORY_LABELS: Record<HintCategory, string> = {
  MUSIC: $localize`:@@game.category.music:Music`,
  FILM: $localize`:@@game.category.film:Film`,
  SPORT: $localize`:@@game.category.sport:Sport`,
  SCIENCE: $localize`:@@game.category.science:Science`,
  WORLD: $localize`:@@game.category.world:World events`,
  CELEBRITIES: $localize`:@@game.category.celebrities:Celebrities`,
};

export function roundTitle(position: number, total: number): string {
  return $localize`:@@game.roundTitle:${GAME_TEXT.round}:round: ${position}:position: of ${total}:total:`;
}
