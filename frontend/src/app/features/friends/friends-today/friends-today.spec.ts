import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { FriendsToday as Today } from '../models';
import { lockedToday } from '../testing';
import { FriendsToday } from './friends-today';

describe('FriendsToday', () => {
  let fixture: ComponentFixture<FriendsToday>;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    fixture = TestBed.createComponent(FriendsToday);
  });

  const render = (today: Today) => {
    fixture.componentRef.setInput('today', today);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  it('asks to finish today’s file first', () => {
    const page = render(lockedToday());

    expect(page.textContent).toContain("Finish today's file to see your friends' scores.");
    expect(page.querySelector('a')?.getAttribute('href')).toBe('/');
    expect(page.querySelectorAll('[data-player]')).toHaveLength(0);
  });

  it('lists the scores, with the player marked and the others’ status', () => {
    const page = render({
      date: '2026-11-10',
      locked: false,
      players: [
        {
          username: 'watson',
          me: false,
          status: 'COMPLETE',
          points: 2600,
          max: 3000,
          rounds: [
            { position: 1, points: 950 },
            { position: 2, points: 600 },
            { position: 3, points: 1000 },
          ],
        },
        {
          username: 'me',
          me: true,
          status: 'COMPLETE',
          points: 1800,
          max: 3000,
          rounds: [{ position: 1, points: 300 }],
        },
        {
          username: 'adler',
          me: false,
          status: 'IN_PROGRESS',
          points: null,
          max: null,
          rounds: null,
        },
        {
          username: 'lestrade',
          me: false,
          status: 'NOT_PLAYED',
          points: null,
          max: null,
          rounds: null,
        },
      ],
    });
    const rows = Array.from(page.querySelectorAll('[data-player]'));

    expect(rows.map((row) => row.textContent?.replace(/\s+/g, ' ').trim())).toEqual([
      'watson 2,600 out of 3,000',
      // Angular drops the spaces between elements: the name and the score touch here.
      'me · you1,800 out of 3,000',
      'adler In progress',
      'lestrade Not played yet',
    ]);
    expect(rows[1].classList).toContain('font-semibold');
    const squares = rows[0].querySelectorAll('i');
    expect(Array.from(squares).map((square) => square.className)).toEqual([
      'size-3 rounded-[3px] bg-score',
      'size-3 rounded-[3px] bg-score/60',
      'size-3 rounded-[3px] bg-score',
    ]);
    expect(rows[1].querySelector('i')?.className).toContain('bg-score/25');
  });
});
