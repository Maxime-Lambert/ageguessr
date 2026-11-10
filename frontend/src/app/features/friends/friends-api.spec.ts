import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FriendsApi, inviteUrl } from './friends-api';
import { friendsOf, relation } from './testing';

describe('FriendsApi', () => {
  let api: FriendsApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(FriendsApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('counts the requests received each time the list is read', () => {
    api.list().subscribe();
    http.expectOne('/api/friends').flush(friendsOf({ incoming: [relation('a'), relation('b')] }));

    expect(api.incomingCount()).toBe(2);
  });

  it('keeps the count when refreshing it fails', () => {
    api.incomingCount.set(3);
    api.refreshCount();
    http.expectOne('/api/friends').flush(null, { status: 500, statusText: 'Error' });

    expect(api.incomingCount()).toBe(3);
  });

  it('calls each endpoint with the relation id or the code, encoded', () => {
    api.sendRequest('Watson').subscribe();
    const send = http.expectOne('/api/friends/requests');
    expect(send.request.method).toBe('POST');
    expect(send.request.body).toEqual({ username: 'Watson' });
    send.flush(null);

    api.accept('a/b').subscribe();
    expect(http.expectOne('/api/friends/requests/a%2Fb/accept').request.method).toBe('POST');
    api.deleteRequest('r1').subscribe();
    expect(http.expectOne('/api/friends/requests/r1').request.method).toBe('DELETE');
    api.remove('f1').subscribe();
    expect(http.expectOne('/api/friends/f1').request.method).toBe('DELETE');
    api.invite().subscribe();
    expect(http.expectOne('/api/friends/invite').request.method).toBe('GET');
    api.regenerateInvite().subscribe();
    expect(http.expectOne('/api/friends/invite').request.method).toBe('POST');
    api.previewInvite('K7F2QX9M').subscribe();
    expect(http.expectOne('/api/friends/invite/K7F2QX9M').request.method).toBe('GET');
    api.acceptInvite('K7F2QX9M').subscribe();
    expect(http.expectOne('/api/friends/invite/K7F2QX9M/accept').request.method).toBe('POST');
    api.today().subscribe();
    http.expectOne('/api/friends/today');
    api.leaderboard().subscribe();
    http.expectOne('/api/friends/leaderboard');
  });

  it('builds the invite link without a language', () => {
    expect(inviteUrl('https://ageguessr.app', 'K7F2QX9M')).toBe(
      'https://ageguessr.app/friends/invite/K7F2QX9M',
    );
  });
});
