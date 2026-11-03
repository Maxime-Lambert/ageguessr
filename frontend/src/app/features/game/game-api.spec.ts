import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { GameApi } from './game-api';
import { round, today } from './testing';

describe('GameApi', () => {
  let api: GameApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(GameApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it("reads today's game", () => {
    let date = '';
    api.today().subscribe((game) => (date = game.date));

    const req = http.expectOne('/api/game/today');
    expect(req.request.method).toBe('GET');
    req.flush(today([round(1)]));
    expect(date).toBe('2026-11-02');
  });

  it('sends the first guess with the language of the round', () => {
    api.firstGuess('2026-11-02', 1, 30, 'FR').subscribe();

    const req = http.expectOne('/api/game/2026-11-02/rounds/1/first-guess');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ guess: 30, language: 'FR' });
    req.flush(round(1));
  });

  it('asks for the hint of a category', () => {
    api.hint('2026-11-02', 2, 'MUSIC').subscribe();

    const req = http.expectOne('/api/game/2026-11-02/rounds/2/hint');
    expect(req.request.body).toEqual({ category: 'MUSIC' });
    req.flush(round(2));
  });

  it('sends the final guess', () => {
    api.secondGuess('2026-11-02', 3, 41).subscribe();

    const req = http.expectOne('/api/game/2026-11-02/rounds/3/second-guess');
    expect(req.request.body).toEqual({ guess: 41 });
    req.flush(round(3));
  });
});
