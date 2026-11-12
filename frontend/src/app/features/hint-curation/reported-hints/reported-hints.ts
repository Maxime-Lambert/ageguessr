import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { reasonsText } from '../../../shared/reports/report-reasons';
import { ApiErrorResponse } from '../../../shared/api-error';
import { HintCurationApi } from '../hint-curation-api';
import { HintReportReason } from '../../../shared/reports/report-reasons';
import { ReportedHint } from '../models';
import { TextInput } from '../../../shared/ui/field/field';
import { Button } from '../../../shared/ui/button/button';

/** The reasons as the player picked them (game-vocabulary), in English like the admin. */
export const HINT_REASON_LABELS: Record<HintReportReason, string> = {
  WRONG_YEAR: 'Wrong year',
  FALSE: 'False',
  UNCLEAR: 'Unclear',
  DISTURBING: 'Bothers the player',
  OTHER: 'Other',
};

const ERROR_MESSAGES: Record<string, string> = {
  HINT_TEXT_BLANK: 'The text cannot be empty.',
  HINT_TEXT_CONTAINS_YEAR:
    'The text cannot contain a year, nor its own in two digits ("May 68"): it would give the answer away.',
};

/**
 * The hints players reported, most reported first, above the translation queue. Correct
 * saves the texts and approves the hint, Reject takes it out of the game, Keep closes its
 * reports. Each closes the reports; nothing is taken out without the admin.
 */
@Component({
  imports: [TextInput, Button],
  selector: 'app-reported-hints',
  templateUrl: './reported-hints.html',
})
export class ReportedHints {
  private readonly api = inject(HintCurationApi);

  readonly items = signal<ReportedHint[]>([]);
  readonly status = signal<'loading' | 'ready' | 'error'>('loading');
  readonly busy = signal<string | null>(null);
  readonly message = signal<string | null>(null);
  /** hintId -> the texts being corrected, from the stored ones. */
  readonly drafts = signal<Record<string, { en: string; fr: string }>>({});

  constructor() {
    this.reload();
  }

  reload(): void {
    this.status.set('loading');
    this.api.reported().subscribe({
      next: (items) => {
        this.items.set(items);
        this.drafts.set(
          Object.fromEntries(
            items.map((item) => [item.hintId, { en: item.textEn ?? '', fr: item.textFr ?? '' }]),
          ),
        );
        this.status.set('ready');
      },
      error: () => this.status.set('error'),
    });
  }

  reasonsText(item: ReportedHint): string {
    return reasonsText(HINT_REASON_LABELS, item.reasons);
  }

  edit(hintId: string, language: 'en' | 'fr', text: string): void {
    this.drafts.update((drafts) => ({
      ...drafts,
      [hintId]: { ...drafts[hintId], [language]: text },
    }));
  }

  /** Saves the texts the hint's audience reads, and keeps it in the game. */
  correct(item: ReportedHint): void {
    const draft = this.drafts()[item.hintId];
    this.act(
      item,
      this.api.review(item.hintId, {
        decision: 'APPROVED',
        ...(item.textEn !== null ? { textEn: draft.en } : {}),
        ...(item.textFr !== null ? { textFr: draft.fr } : {}),
      }),
    );
  }

  reject(item: ReportedHint): void {
    this.act(item, this.api.review(item.hintId, { decision: 'REJECTED' }));
  }

  keep(item: ReportedHint): void {
    this.act(item, this.api.dismissReports(item.hintId));
  }

  private act(item: ReportedHint, call: Observable<unknown>): void {
    if (this.busy()) {
      return;
    }
    this.busy.set(item.hintId);
    this.message.set(null);
    call.subscribe({
      next: () => {
        this.busy.set(null);
        this.items.update((items) => items.filter((other) => other.hintId !== item.hintId));
      },
      error: (error: HttpErrorResponse) => {
        this.busy.set(null);
        const code = (error.error as ApiErrorResponse | null)?.errorCode;
        this.message.set(
          (code && ERROR_MESSAGES[code]) ?? 'Something went wrong. Please try again.',
        );
      },
    });
  }
}
