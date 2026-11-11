import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, Subject, of, throwError } from 'rxjs';
import { GAME_TEXT, HINT_REPORT_REASONS, PHOTO_REPORT_REASONS } from '../game-vocabulary';
import { ReportTarget, RoundReport } from '../models';
import { ReportButton } from './report-button';

describe('ReportButton', () => {
  let fixture: ComponentFixture<ReportButton>;
  let page: HTMLElement;
  let sent: RoundReport[];

  function show(target: ReportTarget, answer: () => Observable<void> = () => of(undefined)): void {
    fixture.componentRef.setInput('target', target);
    fixture.componentRef.setInput('reporter', (report: RoundReport) => {
      sent.push(report);
      return answer();
    });
    fixture.detectChanges();
  }

  function button(text: string): HTMLButtonElement {
    return Array.from(page.querySelectorAll('button')).find((b) => b.textContent?.trim() === text)!;
  }

  function radios(): HTMLInputElement[] {
    return Array.from(page.querySelectorAll<HTMLInputElement>('input[type=radio]'));
  }

  function pick(index: number): void {
    radios()[index].dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function submit(): void {
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  function refused(status: number): Observable<void> {
    return throwError(() => new HttpErrorResponse({ status }));
  }

  beforeEach(() => {
    sent = [];
    fixture = TestBed.createComponent(ReportButton);
    page = fixture.nativeElement as HTMLElement;
  });

  it('opens the reasons of a photo and sends the one picked', () => {
    show('PHOTO');
    expect(page.querySelector('form')).toBeNull();

    button(GAME_TEXT.reportPhoto).click();
    fixture.detectChanges();
    expect(button(GAME_TEXT.reportPhoto).getAttribute('aria-expanded')).toBe('true');
    expect(radios().map((radio) => radio.value)).toEqual(Object.keys(PHOTO_REPORT_REASONS));
    expect(button(GAME_TEXT.reportSend).disabled).toBe(true);

    pick(3);
    submit();

    expect(sent).toEqual([{ target: 'PHOTO', reason: 'LOOKS_MINOR' }]);
    expect(page.querySelector('[role=status]')?.textContent).toContain(GAME_TEXT.reportSent);
    expect(page.querySelector('form')).toBeNull();
  });

  it('lists the reasons of a hint under its own label', () => {
    show('HINT');
    button(GAME_TEXT.reportHint).click();
    fixture.detectChanges();

    expect(radios().map((radio) => radio.value)).toEqual(Object.keys(HINT_REPORT_REASONS));
    expect(radios()[0].name).toBe('report-hint');
  });

  it('sends nothing without a reason, nor twice while sending', () => {
    const answer = new Subject<void>();
    show('PHOTO', () => answer);
    button(GAME_TEXT.reportPhoto).click();
    fixture.detectChanges();

    submit();
    expect(sent).toEqual([]);

    pick(0);
    submit();
    submit();
    expect(sent).toHaveLength(1);
    expect(button(GAME_TEXT.reportSend).disabled).toBe(true);
  });

  it('says so when already reported, without offering it again', () => {
    fixture.componentRef.setInput('reported', true);
    show('PHOTO');

    expect(button(GAME_TEXT.reportPhoto)).toBeUndefined();
    expect(page.textContent).toContain(GAME_TEXT.reportSent);
  });

  it('closes the reasons on cancel', () => {
    show('PHOTO');
    button(GAME_TEXT.reportPhoto).click();
    fixture.detectChanges();

    button(GAME_TEXT.reportCancel).click();
    fixture.detectChanges();

    expect(page.querySelector('form')).toBeNull();
  });

  it.each([
    [429, GAME_TEXT.reportTooMany],
    [409, GAME_TEXT.reportUnavailable],
    [500, GAME_TEXT.genericError],
  ])('explains a refusal (%i) and keeps the reasons open', (status, message) => {
    show('PHOTO', () => refused(status));
    button(GAME_TEXT.reportPhoto).click();
    fixture.detectChanges();
    pick(0);

    submit();

    expect(page.querySelector('[role=alert]')?.textContent).toContain(message);
    expect(page.querySelector('form')).not.toBeNull();
    expect(button(GAME_TEXT.reportSend).disabled).toBe(false);
  });

  it("draws its buttons in the paper's colors on the hint's clipping", () => {
    fixture.componentRef.setInput('tone', 'paper');
    show('HINT');
    button(GAME_TEXT.reportHint).click();
    fixture.detectChanges();

    // The page's foreground is light in the dark theme: on the paper, even disabled, it vanished.
    expect(button(GAME_TEXT.reportSend).className).toContain('text-paper-foreground');
    expect(button(GAME_TEXT.reportSend).className).not.toContain('text-foreground ');
    expect(button(GAME_TEXT.reportCancel).className).toContain('text-paper-muted');
  });
});
