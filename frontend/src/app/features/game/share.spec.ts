import { shareText } from './game-vocabulary';
import { challengeUrl, squares } from './share';
import { finished, round } from './testing';

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
      'Ageguessr · Challenge · 3840 / 5000\n🟩🟨\nhttps://ageguessr.app/challenge/K7F2QX9M',
    );
  });
});
