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
  /** Already reported by this player from this round: shown as sent, not offered again. */
  photoReported: boolean;
  hintReported: boolean;
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

/** Finished playable rounds only, out of 1 000 per playable round. */
export interface GameScore {
  points: number;
  max: number;
  /** No playable round is left to play. */
  complete: boolean;
}

/** What every game screen plays: rounds, one after the other, and their score. */
export interface PlayedRounds {
  rounds: RoundView[];
  score: GameScore;
}

export interface TodayGame extends PlayedRounds {
  date: string;
}

/** The lengths of a free series (FreeSeries.SIZES). */
export const SERIES_SIZES = [5, 10, 20] as const;
export type SeriesSize = (typeof SERIES_SIZES)[number];

/** A free series as the player sees it (/api/game/series). */
export interface Series extends PlayedRounds {
  id: string;
  /** Asked for: `rounds` may hold fewer when the pool ran short. */
  size: number;
  /** The code of the challenge once shared, else null. */
  shareCode: string | null;
  /** Only the creator shares the series. */
  creator: boolean;
}

export type ChallengeStatus = 'NOT_JOINED' | 'PLAYING' | 'FINISHED';

export interface LeaderboardEntry {
  /** Equal points share a rank (1, 1, 3). */
  rank: number;
  name: string;
  /** A nickname rather than an account's username. */
  guest: boolean;
  points: number;
  max: number;
  you: boolean;
}

/** A challenge (/api/game/challenges/{code}): the leaderboard only once finished. */
export interface Challenge {
  code: string;
  size: number;
  rounds: number;
  creatorName: string | null;
  creatorGuest: boolean;
  status: ChallengeStatus;
  /** To play it, once joined. */
  seriesId: string | null;
  score: GameScore | null;
  leaderboard: LeaderboardEntry[];
}

/** What of a round a player reports (POST …/rounds/{position}/report). */
export type ReportTarget = 'PHOTO' | 'HINT';
export type PhotoReportReason =
  | 'TOO_SMALL'
  | 'NOT_IDENTIFIABLE'
  | 'WRONG_PERSON'
  | 'LOOKS_MINOR'
  | 'DISTURBING_PERSON'
  | 'OTHER';
export type HintReportReason = 'WRONG_YEAR' | 'FALSE' | 'UNCLEAR' | 'DISTURBING' | 'OTHER';

/** The reason comes from the list of its target. */
export type RoundReport =
  | { target: 'PHOTO'; reason: PhotoReportReason }
  | { target: 'HINT'; reason: HintReportReason };

/** The guesses an age can take, as the API validates them (no minor on a photo). */
export const MIN_AGE = 18;
export const MAX_AGE = 100;
