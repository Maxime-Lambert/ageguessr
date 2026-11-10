import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { InvitePreview } from '../models';
import { InvitePage } from './invite-page';

describe('InvitePage', () => {
  let fixture: ComponentFixture<InvitePage>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ code: 'K7F2QX9M' }) } },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(InvitePage);
  });

  afterEach(() => http.verify());

  const page = () => {
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  const preview = (value: Partial<InvitePreview> = {}) =>
    http
      .expectOne('/api/friends/invite/K7F2QX9M')
      .flush({ username: 'Holmes', own: false, alreadyFriends: false, ...value });

  const button = (text: string) =>
    Array.from(page().querySelectorAll('button')).find((b) => b.textContent?.trim() === text)!;

  it('shows who sent the link, then makes the two friends', () => {
    preview();
    expect(page().textContent).toContain('Holmes invites you to be friends on AgeGuessr.');

    button('Add as a friend').click();
    http.expectOne({ method: 'POST', url: '/api/friends/invite/K7F2QX9M/accept' }).flush(null);

    expect(page().textContent).toContain('You and Holmes are now friends.');
    expect(page().querySelector('a')?.getAttribute('href')).toBe('/friends');
  });

  it('tells the owner it is their own link', () => {
    preview({ own: true });

    expect(page().textContent).toContain('This is your own invite link.');
    expect(button('Add as a friend')).toBeUndefined();
  });

  it('tells when already friends', () => {
    preview({ alreadyFriends: true });

    expect(page().textContent).toContain('You and Holmes are already friends.');
    expect(button('Add as a friend')).toBeUndefined();
  });

  it('says a replaced or unknown link no longer works', () => {
    http
      .expectOne('/api/friends/invite/K7F2QX9M')
      .flush({ errorCode: 'NOT_FOUND', message: 'x' }, { status: 404, statusText: 'Not Found' });

    expect(page().textContent).toContain('This invite link no longer works.');
  });

  it('says why the friendship was refused', () => {
    preview();
    button('Add as a friend').click();
    http
      .expectOne('/api/friends/invite/K7F2QX9M/accept')
      .flush(
        { errorCode: 'OTHER_FRIEND_LIMIT_REACHED', message: 'x' },
        { status: 409, statusText: 'Conflict' },
      );

    expect(page().querySelector('[role="alert"]')?.textContent).toContain(
      'This player reached the maximum number of friends.',
    );
  });

  it('offers to retry when the preview fails', () => {
    http
      .expectOne('/api/friends/invite/K7F2QX9M')
      .flush(null, { status: 500, statusText: 'Error' });

    button('Try again').click();
    preview();
    expect(page().textContent).toContain('Holmes invites you');
  });
});
