/** Mirrors of the /api/game responses (ageguessr-api features/game). */

export type RoundStep = 'NOT_STARTED' | 'AWAITING_HINT' | 'AWAITING_SECOND_GUESS' | 'FINISHED';
export type RoundStatus = 'PLAYABLE' | 'NEUTRALIZED';
/** OLDER: the person is older than the guess. */
export type Direction = 'OLDER' | 'YOUNGER';
export type HintCategory = 'MUSIC' | 'FILM' | 'SPORT' | 'SCIENCE' | 'WORLD' | 'CELEBRITIES';

/** The hint during the round: no year, it would give the answer away. */
export interface ShownHint {
  category: HintCategory;
  text: string;
}

export interface RevealedHint extends ShownHint {
  year: number;
  sourceUrl: string;
}

export interface RoundReveal {
  age: number;
  firstGuess: number;
  firstPoints: number;
  /** Null after an exact first guess, as secondPoints. */
  secondGuess: number | null;
  secondPoints: number | null;
  points: number;
  person: { name: string; wikipediaUrl: string };
  /** author null when Commons names none. */
  credit: { author: string | null; license: string; licenseUrl: string | null; sourceUrl: string };
  hint: RevealedHint | null;
}

/** A round as the player sees it: nothing of the answer before `result`. */
export interface RoundView {
  position: number;
  status: RoundStatus;
  /** Null when the round is neutralized. */
  imageUrl: string | null;
  step: RoundStep;
  firstGuess: number | null;
  direction: Direction | null;
  categories: HintCategory[] | null;
  hint: ShownHint | null;
  result: RoundReveal | null;
}

export interface TodayGame {
  date: string;
  rounds: RoundView[];
  /** Finished playable rounds only, out of 1 000 per playable round. */
  score: { points: number; max: number; complete: boolean };
}

/** The guesses an age can take, as the API validates them (no minor on a photo). */
export const MIN_AGE = 18;
export const MAX_AGE = 100;
