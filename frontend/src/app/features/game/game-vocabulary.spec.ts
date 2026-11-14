import {
  seriesLength,
  CATEGORY_LABELS,
  GAME_TEXT,
  INVESTIGATION_VOICE,
  NEUTRAL_VOICE,
  ageOutOfRange,
  directionText,
  gapText,
  pickHintsText,
  pickedText,
  rangeText,
  roundTitle,
} from './game-vocabulary';

describe('game vocabulary', () => {
  it('has a text for every word of the game', () => {
    Object.entries(GAME_TEXT).forEach(([key, value]) => {
      expect(value.trim(), key).not.toBe('');
    });
    Object.values(CATEGORY_LABELS).forEach((label) => expect(label.trim()).not.toBe(''));
  });

  it('has a neutral word for every word of the investigation voice', () => {
    expect(Object.keys(NEUTRAL_VOICE).sort()).toEqual(Object.keys(INVESTIGATION_VOICE).sort());
    Object.entries(NEUTRAL_VOICE).forEach(([key, value]) => {
      expect(value.trim(), key).not.toBe('');
    });
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
});
