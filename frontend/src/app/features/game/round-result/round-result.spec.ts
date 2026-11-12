import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { GAME_TEXT, yearsAgo } from '../game-vocabulary';
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
    fixture.componentRef.setInput('reporter', () => of(undefined));
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
    expect(page.textContent).toContain(yearsAgo(finished(1).result!.age));
    expect(link(GAME_TEXT.wikipedia).href).toBe('https://en.wikipedia.org/wiki/Hit');
    expect(link(GAME_TEXT.wikipedia).parentElement!.textContent).toContain('(CC BY-SA)');
  });

  it('credits a hint from Wikidata under CC0', () => {
    const round = finished(1);
    const hint = { ...round.result!.hints[0], sourceUrl: 'https://www.wikidata.org/wiki/Q42' };
    show({ ...round, result: { ...round.result!, hints: [hint] } });

    const credit = link(GAME_TEXT.wikidata);
    expect(credit.href).toBe('https://www.wikidata.org/wiki/Q42');
    // The photo has its own licence: only the hint's line is read.
    expect(credit.parentElement!.textContent).toContain('(CC0)');
  });

  it('closes an exact first guess without a second guess nor hint', () => {
    show(
      finished(1, {
        age: 30,
        firstPoints: 500,
        secondGuess: null,
        secondPoints: null,
        points: 1000,
        hints: [],
      }),
    );

    expect(page.textContent).toContain(GAME_TEXT.exact);
    expect(page.textContent).not.toContain(GAME_TEXT.secondGuess);
    expect(page.textContent).not.toContain(GAME_TEXT.hintFrom);
  });

  it('leads to the next round, or to the summary after the last', () => {
    let next = 0;
    fixture.componentInstance.next.subscribe(() => next++);
    // The last button of the verdict, after the reports.
    const proceed = () =>
      Array.from(page.querySelectorAll<HTMLButtonElement>('button')).find((button) =>
        [GAME_TEXT.next, GAME_TEXT.seeSummary].includes(button.textContent?.trim() ?? ''),
      )!;
    show(finished(1));
    expect(proceed().textContent?.trim()).toBe(GAME_TEXT.next);

    show(finished(3), true);
    proceed().click();

    expect(proceed().textContent?.trim()).toBe(GAME_TEXT.seeSummary);
    expect(next).toBe(1);
  });

  it('shows every hint of the round with its year, and a report for each', () => {
    show(
      finished(1, {
        hints: [
          {
            category: 'MUSIC',
            text: 'A hit.',
            year: 1986,
            sourceUrl: 'https://en.wikipedia.org/wiki/Hit',
          },
          {
            category: 'FILM',
            text: 'A film.',
            year: 1986,
            sourceUrl: 'https://en.wikipedia.org/wiki/Film',
          },
        ],
        reportedHints: ['FILM'],
      }),
    );

    expect(page.textContent).toContain('A hit.');
    expect(page.textContent).toContain('A film.');
    expect(page.textContent?.match(/1986/g)).toHaveLength(2);
    const buttons = Array.from(page.querySelectorAll('button')).map((b) => b.textContent?.trim());
    // The film hint was already reported: only the music one is offered.
    expect(buttons.filter((label) => label === GAME_TEXT.reportHint)).toHaveLength(1);
    expect(page.textContent).toContain(GAME_TEXT.reportSent);
  });

  it('offers to report the photo, and the hint when one was shown', () => {
    show(finished(1));
    const buttons = Array.from(page.querySelectorAll('button')).map((b) => b.textContent?.trim());
    expect(buttons).toContain(GAME_TEXT.reportPhoto);
    expect(buttons).toContain(GAME_TEXT.reportHint);
  });

  it('offers no hint report without a hint, and says what was already reported', () => {
    show(finished(1, { hints: [], photoReported: true }));
    const buttons = Array.from(page.querySelectorAll('button')).map((b) => b.textContent?.trim());
    expect(buttons).not.toContain(GAME_TEXT.reportPhoto);
    expect(buttons).not.toContain(GAME_TEXT.reportHint);
    expect(page.textContent).toContain(GAME_TEXT.reportSent);
  });
});
