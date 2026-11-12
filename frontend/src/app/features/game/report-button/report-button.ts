import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, input, signal } from '@angular/core';
import { LucideFlag } from '@lucide/angular';
import { Observable } from 'rxjs';
import { Button } from '../../../shared/ui/button/button';
import { GAME_TEXT, HINT_REPORT_REASONS, PHOTO_REPORT_REASONS } from '../game-vocabulary';
import { HintCategory, ReportTarget, RoundReport } from '../models';

/** Sends a report of the round on screen: given by the screen, which knows the round. */
export type RoundReporter = (report: RoundReport) => Observable<void>;

/**
 * A discreet flag button by the photo or on the hint of a result, which opens a closed list of
 * reasons (no free text) and sends the one picked. Once sent, here or before a reload,
 * it says so instead of offering it again: one report per photo and per hint of a round.
 */
@Component({
  imports: [Button, LucideFlag],
  selector: 'app-report-button',
  templateUrl: './report-button.html',
  // Open, the list takes the whole width of the row it sits in.
  host: { class: 'block', '[class.basis-full]': 'open()' },
})
export class ReportButton {
  readonly target = input.required<ReportTarget>();
  /** Already reported by this player (RoundReveal), or just now. */
  readonly reported = input(false);
  readonly reporter = input.required<RoundReporter>();
  /** Which of the round's hints, for a HINT report: a result shows one button per hint. */
  readonly hintCategory = input<HintCategory | null>(null);
  /** `paper` on the hint's newspaper clipping, whose colors are not the page's. */
  readonly tone = input<'page' | 'paper'>('page');

  protected readonly text = GAME_TEXT;

  readonly open = signal(false);
  readonly sending = signal(false);
  readonly sent = signal(false);
  readonly error = signal<string | null>(null);
  readonly picked = signal<string | null>(null);

  protected readonly tones = computed(() =>
    this.tone() === 'paper'
      ? {
          trigger: 'text-paper-muted hover:text-paper-foreground',
          muted: 'text-paper-muted',
          send: 'paper-secondary' as const,
          cancel: 'paper-ghost' as const,
        }
      : {
          trigger: 'text-muted hover:text-foreground',
          muted: 'text-muted',
          send: 'secondary' as const,
          cancel: 'ghost' as const,
        },
  );

  readonly done = computed(() => this.reported() || this.sent());
  readonly label = computed(() =>
    this.target() === 'PHOTO' ? this.text.reportPhoto : this.text.reportHint,
  );
  readonly reasons = computed(() =>
    Object.entries(this.target() === 'PHOTO' ? PHOTO_REPORT_REASONS : HINT_REPORT_REASONS).map(
      ([code, label]) => ({ code, label }),
    ),
  );
  /** Ids unique per target and hint: a result shows the photo's and each hint's lists together. */
  readonly name = computed(() =>
    [`report-${this.target().toLowerCase()}`, this.hintCategory()?.toLowerCase()]
      .filter(Boolean)
      .join('-'),
  );

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
    const report = (
      this.target() === 'PHOTO'
        ? { target: 'PHOTO', reason }
        : { target: 'HINT', reason, hintCategory: this.hintCategory() }
    ) as RoundReport;
    this.reporter()(report).subscribe({
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
