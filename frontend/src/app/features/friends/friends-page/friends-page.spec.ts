import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Friends } from '../models';
import { board, friendsOf, lockedToday, relation } from '../testing';
import { FriendsPage } from './friends-page';

describe('FriendsPage', () => {
  let fixture: ComponentFixture<FriendsPage>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(FriendsPage);
  });

  afterEach(() => http.verify());

  const page = () => {
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  const button = (text: string, root: ParentNode = page()) =>
    Array.from(root.querySelectorAll<HTMLButtonElement>('button')).find(
      (b) => b.textContent?.trim() === text,
    )!;

  /** The list, today's scores and the leaderboard, read together after each change. */
  const flushReads = (friends: Friends) => {
    http.expectOne('/api/friends').flush(friends);
    http.expectOne('/api/friends/today').flush(lockedToday());
    http.expectOne('/api/friends/leaderboard').flush(board());
  };

  const open = (friends: Friends = friendsOf()) => {
    flushReads(friends);
    return page();
  };

  it('shows today’s file and the leaderboard, an empty list, and no invite link', () => {
    const view = open();

    expect(view.textContent).not.toContain('invite link');
    expect(view.textContent).not.toContain('Copy the link');
    expect(view.querySelector('app-friends-today')).not.toBeNull();
    expect(view.querySelector('app-friends-leaderboard')).not.toBeNull();
    expect(view.textContent).toContain('No friend yet.');
    expect(view.querySelector('#incoming')).toBeNull();
  });

  it('sends a request by username, then reloads', () => {
    const view = open();
    const input = view.querySelector<HTMLInputElement>('#friend-username')!;
    input.value = ' Watson ';
    input.dispatchEvent(new Event('input'));
    view.querySelector('form')!.dispatchEvent(new Event('submit'));

    const sent = http.expectOne('/api/friends/requests');
    expect(sent.request.body).toEqual({ username: 'Watson' });
    sent.flush(null);
    flushReads(friendsOf({ outgoing: [relation('Watson')] }));

    expect(page().textContent).toContain('Request sent to Watson.');
    expect(page().querySelectorAll('[data-outgoing]')).toHaveLength(1);
  });

  it('says when the username names nobody, without calling the API for an empty one', () => {
    const view = open();
    view.querySelector('form')!.dispatchEvent(new Event('submit'));
    expect(page().textContent).toContain('No player with this username.');

    const input = view.querySelector<HTMLInputElement>('#friend-username')!;
    input.value = 'nobody';
    input.dispatchEvent(new Event('input'));
    view.querySelector('form')!.dispatchEvent(new Event('submit'));
    http
      .expectOne('/api/friends/requests')
      .flush({ errorCode: 'NOT_FOUND', message: 'x' }, { status: 404, statusText: 'Not Found' });

    expect(page().textContent).toContain('No player with this username.');
  });

  it('points to the request received instead of merging a crossing one', () => {
    const view = open();
    const input = view.querySelector<HTMLInputElement>('#friend-username')!;
    input.value = 'Watson';
    input.dispatchEvent(new Event('input'));
    view.querySelector('form')!.dispatchEvent(new Event('submit'));
    http
      .expectOne('/api/friends/requests')
      .flush(
        { errorCode: 'INCOMING_REQUEST_EXISTS', message: 'x' },
        { status: 409, statusText: 'Conflict' },
      );

    expect(page().textContent).toContain('accept it in the requests received');
  });

  it('accepts and declines the requests received', () => {
    const view = open(friendsOf({ incoming: [relation('watson', 'r1'), relation('adler', 'r2')] }));
    const rows = view.querySelectorAll('[data-incoming]');
    expect(view.querySelector('#incoming')?.textContent).toContain('Requests received (2)');

    button('Accept', rows[0]).click();
    http.expectOne({ method: 'POST', url: '/api/friends/requests/r1/accept' }).flush(null);
    flushReads(
      friendsOf({ friends: [relation('watson', 'r1')], incoming: [relation('adler', 'r2')] }),
    );

    button('Decline', page().querySelector('[data-incoming]')!).click();
    http.expectOne({ method: 'DELETE', url: '/api/friends/requests/r2' }).flush(null);
    flushReads(friendsOf({ friends: [relation('watson', 'r1')] }));

    expect(page().querySelector('#incoming')).toBeNull();
    expect(page().querySelectorAll('[data-friend]')).toHaveLength(1);
  });

  it('cancels a request sent', () => {
    open(friendsOf({ outgoing: [relation('moriarty', 'r3')] }));

    button('Cancel', page().querySelector('[data-outgoing]')!).click();
    http.expectOne({ method: 'DELETE', url: '/api/friends/requests/r3' }).flush(null);
    flushReads(friendsOf());

    expect(page().querySelector('[data-outgoing]')).toBeNull();
  });

  it('removes a friend only after confirming', () => {
    open(friendsOf({ friends: [relation('watson', 'f1')] }));
    const row = () => page().querySelector('[data-friend]')!;

    button('Remove', row()).click();
    expect(row().textContent).toContain('Remove watson from your friends?');
    button('No', row()).click();
    expect(row().textContent).not.toContain('from your friends?');
    http.expectNone('/api/friends/f1');

    button('Remove', row()).click();
    button('Yes', row()).click();
    http.expectOne({ method: 'DELETE', url: '/api/friends/f1' }).flush(null);
    flushReads(friendsOf());

    expect(page().querySelector('[data-friend]')).toBeNull();
  });

  it('says when a request is gone, and reloads the list', () => {
    open(friendsOf({ incoming: [relation('watson', 'r1')] }));

    button('Accept', page().querySelector('[data-incoming]')!).click();
    http
      .expectOne('/api/friends/requests/r1/accept')
      .flush({ errorCode: 'NOT_FOUND', message: 'x' }, { status: 404, statusText: 'Not Found' });
    flushReads(friendsOf());

    expect(page().querySelector('[role="alert"]')?.textContent).toContain('no longer there');
  });

  it('offers to retry when the list cannot be loaded', () => {
    http.expectOne('/api/friends').flush(null, { status: 500, statusText: 'Error' });
    http.expectOne('/api/friends/today').flush(null, { status: 500, statusText: 'Error' });
    http.expectOne('/api/friends/leaderboard').flush(null, { status: 500, statusText: 'Error' });

    expect(page().textContent).toContain('Your friends could not be loaded.');
    button('Try again').click();
    flushReads(friendsOf());
    expect(page().textContent).toContain('No friend yet.');
  });
});
