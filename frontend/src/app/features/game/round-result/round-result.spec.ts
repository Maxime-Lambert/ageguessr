import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GAME_TEXT } from '../game-vocabulary';
import { RoundView } from '../models';
import { finished } from '../testing';
import { RoundResult } from './round-result';

describe('RoundResult', () => {
  let fixture: ComponentFixture<RoundResult>;
  let page: HTMLElement;

  function show(view: RoundView, last = false): void {
    fixture.componentRef.setInput('round', view);
    fixture.componentRef.setInput('total', 3);
    fixture.componentRef.setInput('last', last);
    fixture.detectChanges();
  }

  function link(text: string): HTMLAnchorElement {
    return Array.from(page.querySelectorAll('a')).find((a) => a.textContent?.trim() === text)!;
  }

  beforeEach(() => {
    fixture = TestBed.createComponent(RoundResult);
    page = fixture.nativeElement as HTMLElement;
  });

  it('reveals the age, both guesses with their points and the total', () => {
    show(finished(1));

    const text = page.textContent!;
    expect(text).toContain(`${GAME_TEXT.realAge}40`);
    expect(text).toContain(`${GAME_TEXT.firstGuess}30184`);
    expect(text).toContain(`${GAME_TEXT.secondGuess}38409`);
    expect(text).toContain(`593 ${GAME_TEXT.points}`);
  });

  it('names the person with a link to the article in a new tab', () => {
    show(finished(1));

    const name = link('Jane Doe');
    expect(name.href).toBe('https://www.wikidata.org/wiki/Special:GoToLinkedPage/enwiki/Q42');
    expect(name.target).toBe('_blank');
    expect(name.rel).toBe('noopener');
    expect(page.querySelector('img')?.classList).toContain('archive-photo');
  });

  it('credits the photo with its license and source', () => {
    show(finished(1));

    expect(page.textContent).toContain('Jane Photographer');
    expect(link('CC BY-SA 4.0').href).toBe('https://creativecommons.org/licenses/by-sa/4.0');
    expect(link(GAME_TEXT.source).href).toBe('https://commons.wikimedia.org/wiki/File:Jane.jpg');
  });

  it('says the author is unknown when Commons names none', () => {
    show(
      finished(1, {
        credit: { author: null, license: 'CC0', licenseUrl: null, sourceUrl: 'https://c/x' },
      }),
    );

    expect(page.textContent).toContain(GAME_TEXT.unknownAuthor);
    expect(page.textContent).toContain('CC0');
  });

  it('shows the hint again with its year and source', () => {
    show(finished(1));

    expect(page.textContent).toContain('A hit.');
    expect(page.textContent).toContain('1986');
    expect(link(GAME_TEXT.wikipedia).href).toBe('https://en.wikipedia.org/wiki/Hit');
  });

  it('closes an exact first guess without a second guess nor hint', () => {
    show(
      finished(1, {
        age: 30,
        firstPoints: 500,
        secondGuess: null,
        secondPoints: null,
        points: 1000,
        hint: null,
      }),
    );

    expect(page.textContent).toContain(GAME_TEXT.exact);
    expect(page.textContent).not.toContain(GAME_TEXT.secondGuess);
    expect(page.textContent).not.toContain(GAME_TEXT.hintFrom);
  });

  it('leads to the next round, or to the summary after the last', () => {
    let next = 0;
    fixture.componentInstance.next.subscribe(() => next++);
    show(finished(1));
    expect(page.querySelector('button')?.textContent?.trim()).toBe(GAME_TEXT.next);

    show(finished(3), true);
    page.querySelector('button')!.click();

    expect(page.querySelector('button')?.textContent?.trim()).toBe(GAME_TEXT.seeSummary);
    expect(next).toBe(1);
  });
});
