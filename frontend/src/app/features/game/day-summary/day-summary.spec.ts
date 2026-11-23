import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GAME_TEXT } from '../game-vocabulary';
import { finished, round, today } from '../testing';
import { DaySummary } from './day-summary';

describe('DaySummary', () => {
  let fixture: ComponentFixture<DaySummary>;
  let page: HTMLElement;

  beforeEach(() => {
    fixture = TestBed.createComponent(DaySummary);
    page = fixture.nativeElement as HTMLElement;
  });

  it('gives the score of the day and a line per round, a withdrawn photo included', () => {
    fixture.componentRef.setInput(
      'game',
      today([
        finished(1, { points: 593 }),
        round(2, { status: 'NEUTRALIZED', imageUrl: null }),
        finished(3, {
          points: 1000,
          firstGuess: 52,
          secondGuess: null,
          age: 52,
          person: { name: 'John Roe', wikipediaUrl: 'https://w/x' },
        }),
      ]),
    );
    fixture.detectChanges();

    expect(page.querySelector('h2')?.textContent).toContain(GAME_TEXT.summary);
    expect(page.textContent).toContain(`1593 ${GAME_TEXT.outOf} 2000`);
    const lines = Array.from(page.querySelectorAll('li')).map((li) => ({
      text: li.textContent?.replace(/\s+/g, ' ').trim(),
      thumbnail: li.querySelector('img') !== null,
    }));
    expect(lines).toEqual([
      {
        text: `${GAME_TEXT.round} 1 Jane Doe Age 40 · guessed 30 then 38 593 ${GAME_TEXT.points}`,
        thumbnail: true,
      },
      { text: `${GAME_TEXT.round} 2 ${GAME_TEXT.removed}`, thumbnail: false },
      {
        text: `${GAME_TEXT.round} 3 John Roe Age 52 · spot on at 52 1000 ${GAME_TEXT.points}`,
        thumbnail: true,
      },
    ]);
  });

  it('says where the player stands and what the others guessed, given the stats', () => {
    fixture.componentRef.setInput('game', today([finished(1, { points: 593 })]));
    fixture.componentRef.setInput('stats', {
      players: 143,
      betterThanPercent: 72,
      rounds: [{ position: 1, averageFirstGuess: 44 }],
    });
    fixture.detectChanges();

    expect(page.textContent).toContain('Better than 72% of the 143 players who finished this game');
    expect(page.querySelector('li')?.textContent?.replace(/\s+/g, ' ')).toContain(
      "Age 40 · guessed 30 then 38 Players' average: 44",
    );
  });

  it('says nothing of the others under the minimum of players', () => {
    fixture.componentRef.setInput('game', today([finished(1)]));
    fixture.componentRef.setInput('stats', { players: 4, betterThanPercent: null, rounds: [] });
    fixture.detectChanges();

    expect(page.textContent).not.toContain('Better than');
    expect(page.textContent).not.toContain('average');
  });
});
