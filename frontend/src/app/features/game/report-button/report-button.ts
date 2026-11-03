import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, input, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { GAME_TEXT, HINT_REPORT_REASONS, PHOTO_REPORT_REASONS } from '../game-vocabulary';
import { ReportTarget, RoundReport } from '../models';

/** Sends a report of the round on screen: given by the screen, which knows the round. */
export type RoundReporter = (report: RoundReport) => Observable<void>;

/**
 * A discreet link under the photo or the hint of a result, which opens a closed list of
 * reasons (no free text) and sends the one picked. Once sent, here or before a reload,
 * it says so instead of offering it again: one report per round.
 */
@Component({
  selector: 'app-report-button',
  templateUrl: './report-button.html',
})
export class ReportButton {
  readonly target = input.required<ReportTarget>();
  /** Already reported by this player (RoundReveal), or just now. */
  readonly reported = input(false);
  readonly reporter = input.required<RoundReporter>();

  protected readonly text = GAME_TEXT;

  readonly open = signal(false);
  readonly sending = signal(false);
  readonly sent = signal(false);
  readonly error = signal<string | null>(null);
  readonly picked = signal<string | null>(null);

  readonly done = computed(() => this.reported() || this.sent());
  readonly label = computed(() =>
    this.target() === 'PHOTO' ? this.text.reportPhoto : this.text.reportHint,
  );
  readonly reasons = computed(() =>
    Object.entries(this.target() === 'PHOTO' ? PHOTO_REPORT_REASONS : HINT_REPORT_REASONS).map(
      ([code, label]) => ({ code, label }),
    ),
  );
  /** Ids unique per target: a result shows the photo's and the hint's lists together. */
  readonly name = computed(() => `report-${this.target().toLowerCase()}`);

  toggle(): void {
    this.open.update((open) => !open);
    this.error.set(null);
  }

  pick(code: string): void {
    this.picked.set(code);
  }

  send(): void {
    const reason = this.picked();
    if (!reason || this.sending()) {
      return;
    }
    this.sending.set(true);
    this.error.set(null);
    this.reporter()({ target: this.target(), reason } as RoundReport).subscribe({
      next: () => {
        this.sending.set(false);
        this.sent.set(true);
        this.open.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.sending.set(false);
        this.error.set(
          error.status === 429
            ? this.text.reportTooMany
            : error.status === 409
              ? this.text.reportUnavailable
              : this.text.genericError,
        );
      },
    });
  }
}
