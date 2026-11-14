import { formatDate } from '@angular/common';
import { COMMON_MESSAGES } from '../../shared/common-messages';
import {
  Direction,
  GapBand,
  GuessRange,
  HintCategory,
  HintReportReason,
  MAX_AGE,
  MIN_AGE,
  PhotoReportReason,
} from './models';

/**
 * Every word of the game screens comes from here, never from a template
 * (docs/decisions/design.md, « Voix dossier d'enquête »), translated with Angular i18n
 * ($localize, src/locale/messages.fr.xlf). The neutral voice is on trial (2026-11-14,
 * feature/neutral-voice): the investigation voice stays below only to switch back by
 * setting VOICE. Both voices exist in both languages.
 */
export const INVESTIGATION_VOICE = {
  round: $localize`:@@game.voice.round:Case`,
  dailyGame: $localize`:@@game.voice.dailyGame:Today's file`,
  hint: $localize`:@@game.voice.hint:Lead`,
  pickHint: $localize`:@@game.voice.pickHint:Pick a lead`,
  showHints: $localize`:@@game.voice.showHints:Open these leads`,
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
  showHints: $localize`:@@game.neutral.showHints:Show these hints`,
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

const VOICE = NEUTRAL_VOICE;

export const GAME_TEXT = {
  ...VOICE,
  question: $localize`:@@game.question:How old were they on this photo?`,
  photoAlt: $localize`:@@game.photoAlt:The person to guess`,
  // The API picks a fact from (game year − age on the photo): its distance in years is
  // the answer. Never « the year the photo was taken », which is not known to the player.
  pickHintHelp: $localize`:@@game.pickHintHelp:Each one is a fact from as many years ago as this person's age on the photo: a song from 40 years ago means they were 40.`,
  guessLabel: $localize`:@@game.guessLabel:Age`,
  howToPlay: $localize`:@@game.howToPlay:How to play`,
  ruleGuess: $localize`:@@game.ruleGuess:Guess how old the person was on the photo. You get two guesses per round.`,
  ruleLead: $localize`:@@game.ruleLead:After the first, you learn whether they were older or younger, and by how much: 1 to 5 years, 6 to 15, or more.`,
  // No count: it is a setting (app.game.hints.per-round), and fewer when fewer turned up.
  ruleHints: $localize`:@@game.ruleHints:Then you pick a few hints, each a fact from as many years ago as their age on the photo: a film released 40 years ago means they were 40 on it.`,
  rulePoints: $localize`:@@game.rulePoints:Each guess earns up to 500 points, more the closer it is; a spot-on first guess is worth 1000. A new game starts every day at midnight UTC.`,
  gotIt: $localize`:@@game.gotIt:Got it`,
  playToday: $localize`:@@game.playToday:Play today's game`,
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
  // Names and licences, the same in every language.
  wikipedia: 'Wikipedia',
  hintLicense: 'CC BY-SA',
  wikidata: 'Wikidata',
  wikidataLicense: 'CC0',
  photoBy: $localize`:@@game.photoBy:Photo`,
  unknownAuthor: $localize`:@@game.unknownAuthor:Unknown author`,
  source: $localize`:@@game.source:Commons page`,
  loading: $localize`:@@game.loading:Loading…`,
  noGame: $localize`:@@game.noGame:No game today. Come back tomorrow.`,
  retry: $localize`:@@game.retry:Try again`,
  invalidAge: $localize`:@@game.invalidAge:Enter a whole age from ${MIN_AGE}:min: to ${MAX_AGE}:max:.`,
  imageFailed: $localize`:@@game.imageFailed:The photo could not load.`,
  removedExplained: $localize`:@@game.removedExplained:This photo was withdrawn for everyone. It does not count in your score.`,
  closed: $localize`:@@game.closed:Today's game is over. Here is the new one.`,
  genericError: COMMON_MESSAGES.genericError,
  comeBack: $localize`:@@game.comeBack:A new game starts at midnight UTC.`,
  compareFriends: $localize`:@@game.compareFriends:Compare with your friends`,
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

/** The side of a wrong first guess (« Déroulé d'une manche »). */
export function directionText(direction: Direction, guess: number): string {
  return direction === 'OLDER'
    ? $localize`:@@game.older:Older than ${guess}:guess:`
    : $localize`:@@game.younger:Younger than ${guess}:guess:`;
}

/** How far, told with the side: the band of years between the guess and the answer. */
export function gapText(gap: GapBand): string {
  switch (gap) {
    case 'SLIGHTLY':
      return $localize`:@@game.gap.slightly:by 1 to 5 years`;
    case 'CLEARLY':
      return $localize`:@@game.gap.clearly:by 6 to 15 years`;
    case 'FAR':
      return $localize`:@@game.gap.far:by 16 years or more`;
  }
}

/** The ages left for the final guess, the field being kept inside them. */
export function rangeText(range: GuessRange): string {
  return range.min === range.max
    ? $localize`:@@game.rangeOne:Only ${range.min}:age: is left`
    : $localize`:@@game.range:Between ${range.min}:min: and ${range.max}:max:`;
}

/** The final guess out of its range: the bounds are those of the round. */
export function ageOutOfRange(range: GuessRange): string {
  return $localize`:@@game.ageOutOfRange:Enter a whole age from ${range.min}:min: to ${range.max}:max:.`;
}

/** How many leads to pick, and how many are picked so far. */
export function pickHintsText(count: number): string {
  if (count === 1) return GAME_TEXT.pickHint;
  return VOICE === INVESTIGATION_VOICE
    ? $localize`:@@game.voice.pickHints:Pick ${count}:count: leads`
    : $localize`:@@game.neutral.pickHints:Pick ${count}:count: hints`;
}

export function pickedText(picked: number, count: number): string {
  return $localize`:@@game.picked:${picked}:picked: of ${count}:count: picked`;
}

export const CATEGORY_LABELS: Record<HintCategory, string> = {
  MUSIC: $localize`:@@game.category.music:Music`,
  FILM: $localize`:@@game.category.film:Film`,
  SPORT: $localize`:@@game.category.sport:Sport`,
  SCIENCE: $localize`:@@game.category.science:Science`,
  WORLD: $localize`:@@game.category.world:World events`,
  CELEBRITIES: $localize`:@@game.category.celebrities:Celebrities`,
  LITERATURE: $localize`:@@game.category.literature:Literature`,
};

/** How far back the hint is: the answer itself, so only shown once the round is over. */
export function yearsAgo(years: number): string {
  return $localize`:@@game.yearsAgo:${years}:years: years ago`;
}

/** A rough length for a free series, so the choice of size says what it commits to. */
export function seriesDuration(minutes: number): string {
  return $localize`:@@game.seriesDuration:About ${minutes}:minutes: min`;
}

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
    $localize`:@@game.shareText:AgeGuessr · ${GAME_TEXT.challenge}:challenge: · ${points}:points: / ${max}:max:` +
    `\n${squares}\n${url}`
  );
}

/**
 * What each square of the daily share means, in the order of the legend. Only the squares
 * a result holds are explained: a friend who never played reads the grid without guessing.
 */
const SQUARE_LEGEND: [string, string][] = [
  ['🎯', $localize`:@@game.share.legend.exact:first try`],
  ['🟩', $localize`:@@game.share.legend.close:very close`],
  ['🟨', $localize`:@@game.share.legend.near:close`],
  ['🟥', $localize`:@@game.share.legend.far:far off`],
  ['⬛', $localize`:@@game.share.legend.removed:photo withdrawn`],
];

/**
 * The headline of the daily share: the day, then how well the game went, by the share of
 * the points won. Never mocking: the lowest tier says to come back, not that it was bad.
 */
export function dailyShareHeadline(day: string, points: number, max: number): string {
  const ratio = max > 0 ? points / max : 0;
  if (ratio >= 0.9) return $localize`:@@game.share.brilliant:${day}:day:: brilliant!`;
  if (ratio >= 0.7) return $localize`:@@game.share.wellPlayed:${day}:day:: well played!`;
  if (ratio >= 0.5) return $localize`:@@game.share.notBad:${day}:day:: not bad!`;
  if (ratio >= 0.3) return $localize`:@@game.share.tough:${day}:day:: a tough one!`;
  return $localize`:@@game.share.tomorrow:${day}:day:: better luck tomorrow!`;
}

/**
 * The text a player shares at the end of the daily game (« Partage façon Wordle »): a
 * headline with the day and a word on the result, the score, one square per round, what
 * the squares mean, then an invitation to the site. The players found the bare grid
 * unclear (2026-11-14). The day is the game's UTC day (YYYY-MM-DD), formatted in UTC so
 * that it never moves with the player's time zone, in a format of its language.
 */
export function dailyShareText(
  date: string,
  points: number,
  max: number,
  squares: string,
  url: string,
  locale: string,
): string {
  // Midnight UTC of that day: formatDate would read 'YYYY-MM-DD' alone as local midnight,
  // the day before in UTC east of Greenwich.
  const midnightUtc = new Date(`${date}T00:00:00Z`);
  const day = formatDate(midnightUtc, $localize`:@@game.shareDateFormat:MMM d, y`, locale, 'UTC');
  const legend = SQUARE_LEGEND.filter(([square]) => squares.includes(square))
    .map(([square, meaning]) => `${square} ${meaning}`)
    .join(' · ');
  return [
    `AgeGuessr · ${dailyShareHeadline(day, points, max)}`,
    `${points} / ${max} ${GAME_TEXT.points}`,
    squares,
    legend,
    $localize`:@@game.share.invite:Play today's game at ${url}:url:`,
  ].join('\n');
}

/** Why a nickname was refused (400): taken by an account, or not in the format. */
export function nicknameError(errorCode: string | undefined): string {
  return errorCode === 'NICKNAME_TAKEN' ? GAME_TEXT.nicknameTaken : GAME_TEXT.nicknameInvalid;
}
