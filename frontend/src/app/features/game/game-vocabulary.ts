import { COMMON_MESSAGES } from '../../shared/common-messages';
import {
  Direction,
  HintCategory,
  HintReportReason,
  MAX_AGE,
  MIN_AGE,
  PhotoReportReason,
} from './models';

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
  freeMode: $localize`:@@game.voice.freeMode:Free investigation`,
  chooseLength: $localize`:@@game.voice.chooseLength:How many cases?`,
  newSeries: $localize`:@@game.voice.newSeries:New investigation`,
  tryFree: $localize`:@@game.voice.tryFree:Not enough? Open a free investigation`,
  reportHint: $localize`:@@game.voice.reportHint:Report this lead`,
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
  freeMode: $localize`:@@game.neutral.freeMode:Series`,
  chooseLength: $localize`:@@game.neutral.chooseLength:How many rounds?`,
  newSeries: $localize`:@@game.neutral.newSeries:New series`,
  tryFree: $localize`:@@game.neutral.tryFree:Not enough? Play a series`,
  reportHint: $localize`:@@game.neutral.reportHint:Report this hint`,
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
  freeIntro: $localize`:@@game.freeIntro:Same rules as the daily game, as many as you like. Nothing here counts toward the daily game.`,
  noPhotos: $localize`:@@game.noPhotos:No photo is available right now. Try again later.`,
  tooManySeries: $localize`:@@game.tooManySeries:Too many games started from your connection. Try again in a while.`,
  challenge: $localize`:@@game.challenge:Challenge`,
  challengeFriends: $localize`:@@game.challengeFriends:Challenge friends`,
  challengeExplained: $localize`:@@game.challengeExplained:Your friends play the same photos with the same hints, once each. Everyone sees the leaderboard after playing.`,
  nicknameLabel: $localize`:@@game.nicknameLabel:Your name in the challenge`,
  nicknameRules: $localize`:@@game.nicknameRules:3 to 20 letters, digits, "_" or "-". Everyone with the link sees it.`,
  nicknameInvalid: $localize`:@@game.nicknameInvalid:Use 3 to 20 letters, digits, "_" or "-".`,
  nicknameTaken: $localize`:@@game.nicknameTaken:This name belongs to an account. Choose another one.`,
  createChallenge: $localize`:@@game.createChallenge:Create the link`,
  challengeLink: $localize`:@@game.challengeLink:Link to the challenge`,
  share: $localize`:@@game.share:Share`,
  copyLink: $localize`:@@game.copyLink:Copy the link`,
  copied: $localize`:@@game.copied:Copied.`,
  shareResult: $localize`:@@game.shareResult:Share my result`,
  acceptChallenge: $localize`:@@game.acceptChallenge:Take up the challenge`,
  onceOnly: $localize`:@@game.onceOnly:One try each. The leaderboard shows once you have played.`,
  challengeNotFound: $localize`:@@game.challengeNotFound:This challenge does not exist or has expired. A challenge lasts 30 days.`,
  leaderboard: $localize`:@@game.leaderboard:Leaderboard`,
  guestMark: $localize`:@@game.guestMark:guest`,
  youMark: $localize`:@@game.youMark:you`,
  rank: $localize`:@@game.rank:Rank`,
  player: $localize`:@@game.player:Player`,
  reportPhoto: $localize`:@@game.reportPhoto:Report this photo`,
  reportWhy: $localize`:@@game.reportWhy:What is wrong?`,
  reportSend: $localize`:@@game.reportSend:Send`,
  reportCancel: $localize`:@@game.reportCancel:Cancel`,
  reportSent: $localize`:@@game.reportSent:Reported, thank you. We will take a look.`,
  reportTooMany: $localize`:@@game.reportTooMany:Too many reports from your connection. Try again later.`,
  reportUnavailable: $localize`:@@game.reportUnavailable:This can no longer be reported.`,
};

/** The reasons to report a photo, in the order shown (PhotoReportReason, ageguessr-api). */
export const PHOTO_REPORT_REASONS: Record<PhotoReportReason, string> = {
  TOO_SMALL: $localize`:@@game.report.photo.tooSmall:The image is too small`,
  NOT_IDENTIFIABLE: $localize`:@@game.report.photo.notIdentifiable:The person cannot be made out`,
  WRONG_PERSON: $localize`:@@game.report.photo.wrongPerson:It is not the right person`,
  LOOKS_MINOR: $localize`:@@game.report.photo.looksMinor:The person looks under 18`,
  DISTURBING_PERSON: $localize`:@@game.report.photo.disturbing:This person bothers me`,
  OTHER: $localize`:@@game.report.photo.other:Something else`,
};

/** The reasons to report a hint, in the order shown (HintReportReason, ageguessr-api). */
export const HINT_REPORT_REASONS: Record<HintReportReason, string> = {
  WRONG_YEAR: $localize`:@@game.report.hint.wrongYear:The year is wrong`,
  FALSE: $localize`:@@game.report.hint.false:The information is false`,
  UNCLEAR: $localize`:@@game.report.hint.unclear:I do not understand it`,
  DISTURBING: $localize`:@@game.report.hint.disturbing:It bothers me`,
  OTHER: $localize`:@@game.report.hint.other:Something else`,
};

/** A length of series to choose, in the voice of the game. */
export function seriesLength(count: number): string {
  return VOICE === INVESTIGATION_VOICE
    ? $localize`:@@game.voice.length:${count}:count: cases`
    : $localize`:@@game.neutral.length:${count}:count: rounds`;
}

/** A series drawn shorter than asked: the pool ran short. */
export function shorterSeries(count: number): string {
  return $localize`:@@game.shorterSeries:Only ${count}:count: photos could be drawn this time.`;
}

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

/** Who sent the challenge, and how long it is. */
export function challengeIntro(creator: string | null, rounds: number): string {
  return creator
    ? $localize`:@@game.challengeIntro:${creator}:creator: challenges you on ${seriesLength(rounds)}:length:.`
    : $localize`:@@game.challengeIntroAnonymous:A friend challenges you on ${seriesLength(rounds)}:length:.`;
}

/**
 * The text a player shares: the score, one square per round (green from 900 points,
 * yellow from 500, red below, black for a withdrawn photo), then the link. Nothing of
 * the answers: the friends have yet to play.
 */
export function shareText(points: number, max: number, squares: string, url: string): string {
  return (
    $localize`:@@game.shareText:Ageguessr · ${GAME_TEXT.challenge}:challenge: · ${points}:points: / ${max}:max:` +
    `\n${squares}\n${url}`
  );
}

/** Why a nickname was refused (400): taken by an account, or not in the format. */
export function nicknameError(errorCode: string | undefined): string {
  return errorCode === 'NICKNAME_TAKEN' ? GAME_TEXT.nicknameTaken : GAME_TEXT.nicknameInvalid;
}
