import {
  seriesLength,
  CATEGORY_LABELS,
  GAME_TEXT,
  ageOutOfRange,
  directionText,
  gapText,
  pickHintsText,
  pickedText,
  rangeText,
  roundTitle,
  localMidnight,
  nextGameIn,
  ruleNewGame,
  summaryDetail,
} from './game-vocabulary';
import { vi } from 'vitest';

describe('game vocabulary', () => {
  it('has a text for every word of the game', () => {
    Object.entries(GAME_TEXT).forEach(([key, value]) => {
      expect(value.trim(), key).not.toBe('');
    });
    Object.values(CATEGORY_LABELS).forEach((label) => expect(label.trim()).not.toBe(''));
  });

  it('gives the side, then the band of years apart', () => {
    expect(directionText('OLDER', 30)).toBe('Older than 30');
    expect(directionText('YOUNGER', 30)).toBe('Younger than 30');
    expect(gapText('SLIGHTLY')).toBe('by 1 to 5 years');
    expect(gapText('CLEARLY')).toBe('by 6 to 15 years');
    expect(gapText('FAR')).toBe('by 16 years or more');
  });

  it('gives the ages left, and refuses a guess outside them by name', () => {
    expect(rangeText({ min: 36, max: 45 })).toBe('Between 36 and 45');
    expect(rangeText({ min: 100, max: 100 })).toBe('Only 100 is left');
    expect(ageOutOfRange({ min: 36, max: 45 })).toBe('Enter a whole age from 36 to 45.');
  });

  it('asks for one hint in the singular, several with a count', () => {
    expect(pickHintsText(1)).toBe(GAME_TEXT.pickHint);
    expect(pickHintsText(3)).toBe('Pick 3 hints');
    expect(pickedText(2, 3)).toBe('2 of 3 picked');
  });

  it('numbers the rounds of the day', () => {
    expect(roundTitle(2, 3)).toBe(`${GAME_TEXT.round} 2 of 3`);
  });

  it('counts a single round in the singular', () => {
    expect(seriesLength(1)).toBe('1 round');
    expect(seriesLength(5)).toBe('5 rounds');
  });

  it('counts the time to the next game in hours and minutes, minutes alone at the end', () => {
    expect(nextGameIn((5 * 60 + 12) * 60_000)).toBe('Next game in 5 h 12 min');
    expect(nextGameIn((2 * 60 + 5) * 60_000)).toBe('Next game in 2 h 05 min');
    expect(nextGameIn(3 * 60_000)).toBe('Next game in 3 min');
    // Seconds round up: never « 0 min » while the game is still to come.
    expect(nextGameIn(20_000)).toBe('Next game in 1 min');
  });

  it('gives midnight UTC on the player clock, and says nothing more on UTC', () => {
    const now = new Date('2026-11-23T12:00:00Z');
    vi.spyOn(now, 'getTimezoneOffset').mockReturnValue(0);
    expect(localMidnight(now, 'en-US')).toBeNull();
    expect(ruleNewGame(null)).toBe('A new game starts every day at midnight UTC.');
    expect(ruleNewGame('1:00 AM')).toBe(
      'A new game starts every day at midnight UTC (1:00 AM your time).',
    );
  });

  it('formats midnight UTC in the browser time zone otherwise', () => {
    const now = new Date('2026-11-23T12:00:00Z');
    vi.spyOn(now, 'getTimezoneOffset').mockReturnValue(-60);
    const expected = new Date('2026-11-24T00:00:00Z').toLocaleTimeString('en-US', {
      hour: 'numeric',
      minute: '2-digit',
    });
    expect(localMidnight(now, 'en-US')?.replace(/\s/g, ' ')).toBe(expected.replace(/\s/g, ' '));
  });

  it('recalls a round on the summary with the age and the guesses', () => {
    expect(summaryDetail(40, 30, 38)).toBe('Age 40 · guessed 30 then 38');
    expect(summaryDetail(52, 52, null)).toBe('Age 52 · spot on at 52');
  });
});
