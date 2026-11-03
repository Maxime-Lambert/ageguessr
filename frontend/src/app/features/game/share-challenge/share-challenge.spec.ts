import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Auth } from '../../auth/auth';
import { GAME_TEXT } from '../game-vocabulary';
import { Series } from '../models';
import { SHARE_TARGET } from '../share';
import { challenge, fakeShareTarget, finished, series } from '../testing';
import { ShareChallenge } from './share-challenge';

describe('ShareChallenge', () => {
  let fixture: ComponentFixture<ShareChallenge>;
  let http: HttpTestingController;
  let page: HTMLElement;
  let share: ReturnType<typeof fakeShareTarget>;

  function render(played: Series, canShare = false): void {
    share = fakeShareTarget(canShare);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SHARE_TARGET, useValue: share.target },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ShareChallenge);
    fixture.componentRef.setInput('series', played);
    fixture.detectChanges();
    page = fixture.nativeElement as HTMLElement;
  }

  afterEach(() => http.verify());

  function button(text: string): HTMLButtonElement {
    return Array.from(page.querySelectorAll('button')).find((b) => b.textContent?.trim() === text)!;
  }

  const played = series([finished(1, { points: 1000 }), finished(2, { points: 300 })]);

  it('asks a guest for a name, then gives the link to copy with the score', async () => {
    render(played);
    button(GAME_TEXT.challengeFriends).click();
    fixture.detectChanges();

    button(GAME_TEXT.createChallenge).click();
    fixture.detectChanges();
    expect(page.textContent).toContain(GAME_TEXT.nicknameInvalid);
    http.expectNone('/api/game/series/series-1/share');

    const input = page.querySelector<HTMLInputElement>('#nickname')!;
    input.value = 'Sherlock';
    input.dispatchEvent(new Event('input'));
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    const request = http.expectOne('/api/game/series/series-1/share');
    expect(request.request.body).toEqual({ nickname: 'Sherlock' });
    request.flush(challenge({ status: 'FINISHED' }));
    fixture.detectChanges();

    expect(page.querySelector<HTMLInputElement>('#challenge-link')!.value).toBe(
      'https://ageguessr.app/challenge/K7F2QX9M',
    );
    expect(button(GAME_TEXT.share)).toBeUndefined();
    button(GAME_TEXT.copyLink).click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(share.handed.copied).toEqual([
      'Ageguessr · Challenge · 1300 / 2000\n🟩🟥\nhttps://ageguessr.app/challenge/K7F2QX9M',
    ]);
    expect(page.textContent).toContain(GAME_TEXT.copied);
  });

  it('shares at once for an account, which plays under its username', () => {
    render(played, true);
    TestBed.inject(Auth).currentUser.set({
      id: 'u1',
      email: 'a@x.com',
      username: 'holmes',
      emailVerified: true,
      role: 'USER',
      language: 'EN',
    });

    button(GAME_TEXT.challengeFriends).click();
    const request = http.expectOne('/api/game/series/series-1/share');
    expect(request.request.body).toEqual({ nickname: null });
    request.flush(challenge({ status: 'FINISHED' }));
    fixture.detectChanges();

    button(GAME_TEXT.share).click();
    expect(share.handed.shared).toHaveLength(1);
  });

  it('gives the same link again for a series already shared, without a name', () => {
    render(series([finished(1)], { shareCode: 'K7F2QX9M' }));

    button(GAME_TEXT.challengeFriends).click();
    http.expectOne('/api/game/series/series-1/share').flush(challenge({ status: 'FINISHED' }));
    fixture.detectChanges();

    expect(page.querySelector('#nickname')).toBeNull();
    expect(page.querySelector('#challenge-link')).not.toBeNull();
  });
});
