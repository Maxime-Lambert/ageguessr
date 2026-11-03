import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { GAME_TEXT, seriesLength } from '../game-vocabulary';
import { Series } from '../models';
import { finished, round, series } from '../testing';
import { FreeGame } from './free-game';

describe('FreeGame', () => {
  let fixture: ComponentFixture<FreeGame>;
  let http: HttpTestingController;
  let page: HTMLElement;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(FreeGame);
    page = fixture.nativeElement as HTMLElement;
  });

  afterEach(() => http.verify());

  function noCurrentSeries(): void {
    http
      .expectOne('/api/game/series/current')
      .flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();
  }

  function current(found: Series): void {
    http.expectOne('/api/game/series/current').flush(found);
    fixture.detectChanges();
  }

  function button(text: string): HTMLButtonElement {
    return Array.from(page.querySelectorAll('button')).find((b) => b.textContent?.trim() === text)!;
  }

  function guess(value: string): void {
    const input = page.querySelector<HTMLInputElement>('#guess')!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  it('lets the player pick a length, then plays the series drawn', () => {
    noCurrentSeries();
    expect(page.textContent).toContain(GAME_TEXT.chooseLength);

    button(seriesLength(10)).click();
    const start = http.expectOne('/api/game/series');
    expect(start.request.method).toBe('POST');
    expect(start.request.body).toEqual({ size: 10 });
    start.flush(series([round(1), round(2)], { size: 10 }));
    fixture.detectChanges();

    expect(page.querySelector('h2')?.textContent).toContain(`${GAME_TEXT.round} 1 of 2`);
    expect(page.textContent).toContain('Only 2 photos could be drawn this time.');
  });

  it('picks up the series left unfinished and plays it on its own URLs', () => {
    current(series([finished(1), round(2)]));
    expect(page.querySelector('h2')?.textContent).toContain(`${GAME_TEXT.round} 2 of 2`);

    guess('30');
    const first = http.expectOne('/api/game/series/series-1/rounds/2/first-guess');
    expect(first.request.body).toEqual({ guess: 30, language: 'EN' });
    first.flush(finished(2));
    fixture.detectChanges();

    button(GAME_TEXT.seeSummary).click();
    http.expectOne('/api/game/series/series-1').flush(series([finished(1), finished(2)]));
    fixture.detectChanges();
    expect(page.textContent).toContain(GAME_TEXT.summary);
    expect(page.textContent).toContain('1186');
  });

  it('starts over with a new series from the summary', () => {
    current(series([finished(1)], { score: { points: 593, max: 1000, complete: true } }));

    button(GAME_TEXT.newSeries).click();
    fixture.detectChanges();

    expect(page.textContent).toContain(GAME_TEXT.chooseLength);
    expect(button(seriesLength(5))).toBeTruthy();
  });

  it('says so when no photo can be drawn', () => {
    noCurrentSeries();

    button(seriesLength(5)).click();
    http
      .expectOne('/api/game/series')
      .flush({ errorCode: 'NO_PHOTOS', message: 'x' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain(GAME_TEXT.noPhotos);
  });

  it('offers to retry when the series cannot be loaded', () => {
    http
      .expectOne('/api/game/series/current')
      .flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    button(GAME_TEXT.retry).click();
    fixture.detectChanges();
    expect(page.textContent).toContain(GAME_TEXT.chooseLength);
  });
});
