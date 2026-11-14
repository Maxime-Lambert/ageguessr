import { dailyShareHeadline, dailyShareText, shareText } from './game-vocabulary';
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

  it('tells the day, how it went, the score, the squares and what they mean, then invites', () => {
    expect(
      dailyShareText(
        '2026-11-13',
        2340,
        3000,
        '🎯🟩🟥',
        dailyShareUrl('https://ageguessr.app'),
        'en-US',
      ),
    ).toBe(
      'AgeGuessr · Case of Nov 13, 2026 cracked with flair!\n' +
        '2340 / 3000 points\n' +
        '🎯🟩🟥\n' +
        '🎯 first try · 🟩 very close · 🟥 far off\n' +
        "Take on today's case at https://ageguessr.app",
    );
  });

  it('explains only the squares the result holds, in a fixed order', () => {
    const text = dailyShareText('2026-11-13', 1000, 3000, '🟥⬛🟨', '', 'en-US');

    expect(text).toContain('\n🟨 close · 🟥 far off · ⬛ photo withdrawn\n');
    expect(text).not.toContain('first try');
  });

  it('words the headline by the share of the points won, never mocking', () => {
    const day = 'Nov 13, 2026';
    expect(dailyShareHeadline(day, 2700, 3000)).toBe('Case of Nov 13, 2026 cracked brilliantly!');
    expect(dailyShareHeadline(day, 2699, 3000)).toBe('Case of Nov 13, 2026 cracked with flair!');
    expect(dailyShareHeadline(day, 1500, 3000)).toBe('Case of Nov 13, 2026 wrapped up!');
    expect(dailyShareHeadline(day, 900, 3000)).toBe('Case of Nov 13, 2026 solved by a whisker!');
    expect(dailyShareHeadline(day, 899, 3000)).toBe(
      'Case of Nov 13, 2026: the trail went cold… see you tomorrow!',
    );
    // Every photo withdrawn: nothing to win, nothing to divide by.
    expect(dailyShareHeadline(day, 0, 0)).toContain('the trail went cold');
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
