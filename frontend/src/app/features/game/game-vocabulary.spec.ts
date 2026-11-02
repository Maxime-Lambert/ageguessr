import {
  CATEGORY_LABELS,
  GAME_TEXT,
  INVESTIGATION_VOICE,
  NEUTRAL_VOICE,
  directionText,
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

  it('gives the side only, never how far', () => {
    expect(directionText('OLDER', 30)).toBe('Older than 30');
    expect(directionText('YOUNGER', 30)).toBe('Younger than 30');
  });

  it('numbers the rounds of the day', () => {
    expect(roundTitle(2, 3)).toBe(`${GAME_TEXT.round} 2 of 3`);
  });
});
