import { dailyShareText, dailyShareVerdict, shareText } from './game-vocabulary';
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

  it('says the day, what was guessed, the squares and the score, then dares', () => {
    expect(
      dailyShareText(
        '2026-11-13',
        2340,
        3000,
        '🎯🟩🟥',
        3,
        dailyShareUrl('https://ageguessr.app'),
        'en-US',
      ),
    ).toBe(
      'AgeGuessr · Nov 13, 2026\n' +
        'I guessed the age of 3 people in photos 📸\n' +
        '🎯🟩🟥  2,340 / 3,000\n' +
        'Well played! Can you beat that? 👉 https://ageguessr.app',
    );
  });

  it('counts a single photo in the singular, and drops the line with none left', () => {
    expect(dailyShareText('2026-11-13', 900, 1000, '🟩', 1, '', 'en-US')).toContain(
      '\nI guessed the age of 1 person in a photo 📸\n',
    );
    const allWithdrawn = dailyShareText('2026-11-13', 0, 0, '⬛⬛⬛', 0, '', 'en-US');
    expect(allWithdrawn).not.toContain('I guessed');
    expect(allWithdrawn).toBe(
      'AgeGuessr · Nov 13, 2026\n⬛⬛⬛  0 / 0\nRough day! Can you beat that? 👉 ',
    );
  });

  it('words the verdict by the share of the points won, never mocking', () => {
    expect(dailyShareVerdict(2700, 3000)).toBe('Brilliant!');
    expect(dailyShareVerdict(2699, 3000)).toBe('Well played!');
    expect(dailyShareVerdict(2100, 3000)).toBe('Well played!');
    expect(dailyShareVerdict(2099, 3000)).toBe('Not bad!');
    expect(dailyShareVerdict(1500, 3000)).toBe('Not bad!');
    expect(dailyShareVerdict(1499, 3000)).toBe('A tough one!');
    expect(dailyShareVerdict(900, 3000)).toBe('A tough one!');
    expect(dailyShareVerdict(899, 3000)).toBe('Rough day!');
    // Every photo withdrawn: nothing to win, nothing to divide by.
    expect(dailyShareVerdict(0, 0)).toBe('Rough day!');
  });

  it('keeps the UTC day of the game, whatever the time zone', () => {
    expect(dailyShareText('2026-01-01', 0, 3000, '', 0, '', 'en-US')).toContain('Jan 1, 2026');
    expect(dailyShareText('2026-12-31', 0, 3000, '', 0, '', 'en-US')).toContain('Dec 31, 2026');
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
