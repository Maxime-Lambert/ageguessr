import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { GAME_TEXT } from '../game-vocabulary';
import { HowToPlayPage } from './how-to-play-page';

describe('HowToPlayPage', () => {
  it('titles the rules once, and leads to the daily game', () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(HowToPlayPage);
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;

    expect(page.querySelector('h1')?.textContent).toContain(GAME_TEXT.howToPlay);
    expect(page.querySelector('h2')).toBeNull();
    expect(page.querySelectorAll('li')).toHaveLength(4);
    expect(page.querySelector('a')?.getAttribute('href')).toBe('/');
  });
});
