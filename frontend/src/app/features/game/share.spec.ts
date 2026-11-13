import { dailyShareText, shareText } from './game-vocabulary';
import { challengeUrl, dailyShareUrl, shareOrCopy, squares } from './share';
import { fakeShareTarget, finished, round } from './testing';

describe('share', () => {
  it('links to a challenge without the language, so that each friend gets theirs', () => {
    expect(challengeUrl('https://ageguessr.app', 'K7F2QX9M')).toBe(
      'https://ageguessr.app/challenge/K7F2QX9M',
    );
  });

  it('draws one square per round by points, never an answer', () => {
    const rounds = [
      finished(1, { points: 1000 }),
      finished(2, { points: 900 }),
      finished(3, { points: 600 }),
      finished(4, { points: 120 }),
      round(5, { status: 'NEUTRALIZED', imageUrl: null }),
    ];

    expect(squares(rounds)).toBe('🟩🟩🟨🟥⬛');
  });

  it('puts the score, the squares and the link in the text to share', () => {
    expect(shareText(3840, 5000, '🟩🟨', 'https://ageguessr.app/challenge/K7F2QX9M')).toBe(
      'AgeGuessr · Challenge · 3840 / 5000\n🟩🟨\nhttps://ageguessr.app/challenge/K7F2QX9M',
    );
  });

  it('marks a round found at the first guess with a target, for the daily game only', () => {
    const rounds = [
      finished(1, { points: 1000, secondGuess: null, secondPoints: null }),
      finished(2, { points: 950 }),
      finished(3, { points: 120 }),
      round(4, { status: 'NEUTRALIZED', imageUrl: null }),
    ];

    expect(squares(rounds, { exact: true })).toBe('🎯🟩🟥⬛');
    expect(squares(rounds)).toBe('🟩🟩🟥⬛');
  });

  it('puts the day, the score, the squares and the site in the daily text', () => {
    expect(
      dailyShareText(
        '2026-11-13',
        2340,
        3000,
        '🎯🟩🟥',
        dailyShareUrl('https://ageguessr.app'),
        'en-US',
      ),
    ).toBe('AgeGuessr · Nov 13, 2026 · 2340 / 3000\n🎯🟩🟥\nhttps://ageguessr.app');
  });

  it('keeps the UTC day of the game, whatever the time zone', () => {
    expect(dailyShareText('2026-01-01', 0, 3000, '', '', 'en-US')).toContain('Jan 1, 2026');
    expect(dailyShareText('2026-12-31', 0, 3000, '', '', 'en-US')).toContain('Dec 31, 2026');
  });

  it('hands the text to the share sheet when there is one, else to the clipboard', async () => {
    const phone = fakeShareTarget(true);
    expect(await shareOrCopy(phone.target, 'text')).toBe('shared');
    expect(phone.handed).toEqual({ shared: ['text'], copied: [] });

    const computer = fakeShareTarget(false);
    expect(await shareOrCopy(computer.target, 'text')).toBe('copied');
    expect(computer.handed).toEqual({ shared: [], copied: ['text'] });
  });

  it('takes a cancelled share for no error, and a refused copy for one', async () => {
    const cancelled = fakeShareTarget(true);
    cancelled.target.share = () => Promise.reject(new DOMException('', 'AbortError'));
    expect(await shareOrCopy(cancelled.target, 'text')).toBe('shared');

    const refused = fakeShareTarget(false);
    refused.target.copy = () => Promise.reject(new DOMException('', 'NotAllowedError'));
    await expect(shareOrCopy(refused.target, 'text')).rejects.toBeInstanceOf(DOMException);
  });
});
