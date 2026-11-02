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
        finished(3, { points: 1000, person: { name: 'John Roe', wikipediaUrl: 'https://w/x' } }),
      ]),
    );
    fixture.detectChanges();

    expect(page.querySelector('h2')?.textContent).toContain(GAME_TEXT.summary);
    expect(page.textContent).toContain(`1593 ${GAME_TEXT.outOf} 2000`);
    const lines = Array.from(page.querySelectorAll('li')).map((li) =>
      Array.from(li.children).map((part) => part.textContent?.trim()),
    );
    expect(lines).toEqual([
      [`${GAME_TEXT.round} 1 · Jane Doe`, `593 ${GAME_TEXT.points}`],
      [`${GAME_TEXT.round} 2 · ${GAME_TEXT.removed}`],
      [`${GAME_TEXT.round} 3 · John Roe`, `1000 ${GAME_TEXT.points}`],
    ]);
  });
});
