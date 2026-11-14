import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FriendsLeaderboard as Board } from '../models';
import { board } from '../testing';
import { FriendsLeaderboard } from './friends-leaderboard';

describe('FriendsLeaderboard', () => {
  let fixture: ComponentFixture<FriendsLeaderboard>;

  beforeEach(() => {
    fixture = TestBed.createComponent(FriendsLeaderboard);
  });

  const render = (value: Board) => {
    fixture.componentRef.setInput('board', value);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  it('ranks by average, shares a tie and puts a player without a file last', () => {
    const page = render({
      players: [
        {
          rank: 1,
          username: 'watson',
          me: false,
          gamesPlayed: 9,
          averagePoints: 2400,
          currentStreak: 3,
        },
        {
          rank: 1,
          username: 'me',
          me: true,
          gamesPlayed: 9,
          averagePoints: 2400,
          currentStreak: 5,
        },
        {
          rank: null,
          username: 'idle',
          me: false,
          gamesPlayed: 0,
          averagePoints: null,
          currentStreak: 0,
        },
      ],
    });
    const rows = Array.from(page.querySelectorAll('tbody tr'));

    expect(
      rows.map((row) =>
        Array.from(row.querySelectorAll('td'))
          .map((cell) => cell.textContent?.replace(/\s+/g, ' ').trim())
          .join(' '),
      ),
    ).toEqual(['1 watson 2,400 9 3', '1 me · you 2,400 9 5', '– idle No game yet 0 0']);
    expect(rows[1].classList).toContain('font-semibold');
    expect(page.textContent).not.toContain('Add friends to compare');
  });

  it('invites to add friends when alone', () => {
    expect(render(board()).textContent).toContain('Add friends to compare your scores.');
  });
});
