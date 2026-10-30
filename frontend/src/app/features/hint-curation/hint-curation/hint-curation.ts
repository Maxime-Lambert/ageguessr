import { ApiErrorResponse } from '../../../shared/api-error';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, linkedSignal, signal } from '@angular/core';
import { HintCurationApi } from '../hint-curation-api';
import {
  CoverageCell,
  HintCategory,
  HintCoverage,
  HintReview,
  HintReviewStatus,
  PendingHint,
} from '../models';

/** How many hints to ask for at once, and when to ask for more. */
const BATCH_SIZE = 20;
const REFILL_BELOW = 5;

const GENERIC_ERROR = 'Something went wrong. Please try again.';

const CATEGORY_LABELS: Record<HintCategory, string> = {
  MUSIC: 'Music',
  FILM: 'Film',
  SPORT: 'Sport',
  SCIENCE: 'Science',
  WORLD: 'World',
  CELEBRITIES: 'Celebrities',
};

const CATEGORIES = Object.keys(CATEGORY_LABELS) as HintCategory[];

const ERROR_MESSAGES: Record<string, string> = {
  HINT_TEXT_BLANK: 'The text cannot be empty.',
  HINT_TEXT_CONTAINS_YEAR: 'The text cannot contain a year: it would give the answer away.',
};

/**
 * Validation of the translated hints, the only ones an admin reads: the others enter the
 * game directly (plans/active-plan.md). Below the queue, the coverage shows where a
 * category is hidden for lack of approved hints.
 */
@Component({
  selector: 'app-hint-curation',
  templateUrl: './hint-curation.html',
  host: { '(document:keydown)': 'onKeydown($event)' },
})
export class HintCuration {
  private readonly api = inject(HintCurationApi);

  readonly queue = signal<PendingHint[]>([]);
  readonly totalPending = signal(0);
  readonly current = computed(() => this.queue()[0] ?? null);
  readonly status = signal<'loading' | 'ready' | 'error'>('loading');
  readonly busy = signal(false);
  readonly message = signal<string | null>(null);
  readonly lastAction = signal<PendingHint | null>(null);
  readonly coverage = signal<HintCoverage | null>(null);
  readonly coverageFailed = signal(false);
  /** What the admin is typing: starts from the current hint's text. */
  readonly draftEn = linkedSignal(() => this.current()?.textEn ?? '');
  readonly draftFr = linkedSignal(() => this.current()?.textFr ?? '');

  protected readonly categories = CATEGORIES;
  protected readonly categoryLabels = CATEGORY_LABELS;

  /** Skipped this session: not shown again until the page is reloaded. */
  private readonly skipped = new Set<string>();
  private refilling = false;

  constructor() {
    this.reload();
    this.reloadCoverage();
  }

  reload(): void {
    this.queue.set([]);
    this.status.set('loading');
    this.api.pending(BATCH_SIZE).subscribe({
      next: (page) => {
        this.totalPending.set(page.totalPending);
        this.queue.set(page.items.filter((item) => !this.skipped.has(item.hintId)));
        this.status.set('ready');
      },
      error: () => this.status.set('error'),
    });
  }

  reloadCoverage(): void {
    this.coverageFailed.set(false);
    this.api.coverage().subscribe({
      next: (coverage) => this.coverage.set(coverage),
      error: () => this.coverageFailed.set(true),
    });
  }

  decide(decision: HintReviewStatus): void {
    const item = this.current();
    if (!item || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.message.set(null);
    this.api.review(item.hintId, this.reviewOf(item, decision)).subscribe({
      next: (result) => {
        this.lastAction.set({ ...item, textEn: result.textEn, textFr: result.textFr });
        this.totalPending.update((n) => Math.max(0, n - 1));
        this.queue.update((queue) => queue.slice(1));
        this.busy.set(false);
        this.refill();
        this.reloadCoverage();
      },
      error: (error: HttpErrorResponse) => {
        this.message.set(errorMessage(error));
        this.busy.set(false);
      },
    });
  }

  skip(): void {
    const item = this.current();
    if (!item || this.busy()) {
      return;
    }
    this.skipped.add(item.hintId);
    this.queue.update((queue) => queue.slice(1));
    this.refill();
  }

  /** Back to PENDING, and back on top of the queue. */
  undo(): void {
    const last = this.lastAction();
    if (!last || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.message.set(null);
    this.api.review(last.hintId, { decision: 'PENDING' }).subscribe({
      next: () => {
        this.lastAction.set(null);
        this.queue.update((queue) => [last, ...queue]);
        this.totalPending.update((n) => n + 1);
        this.busy.set(false);
        this.reloadCoverage();
      },
      error: (error: HttpErrorResponse) => {
        this.message.set(errorMessage(error));
        this.busy.set(false);
      },
    });
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.ctrlKey || event.metaKey || event.altKey) {
      return;
    }
    // Correcting a text must never approve or reject the hint.
    const target = event.target as HTMLElement | null;
    if (target?.closest('input, textarea, select, [contenteditable="true"]')) {
      return;
    }
    const actions: Record<string, () => void> = {
      a: () => this.decide('APPROVED'),
      r: () => this.decide('REJECTED'),
      u: () => this.undo(),
      arrowright: () => this.skip(),
    };
    const action = actions[event.key.toLowerCase()];
    if (action) {
      event.preventDefault();
      action();
    }
  }

  onDraft(language: 'en' | 'fr', event: Event): void {
    const value = (event.target as HTMLTextAreaElement).value;
    (language === 'en' ? this.draftEn : this.draftFr).set(value);
  }

  cell(cells: CoverageCell[], category: HintCategory): CoverageCell {
    return cells.find((c) => c.category === category) ?? { category, fr: 0, en: 0 };
  }

  isGap(cell: CoverageCell, minimum: number): boolean {
    return cell.fr < minimum || cell.en < minimum;
  }

  /** Only a text the admin changed is sent: the API then leaves the other one alone. */
  private reviewOf(item: PendingHint, decision: HintReviewStatus): HintReview {
    const review: HintReview = { decision };
    if (item.textEn !== null && this.draftEn() !== item.textEn) {
      review.textEn = this.draftEn();
    }
    if (item.textFr !== null && this.draftFr() !== item.textFr) {
      review.textFr = this.draftFr();
    }
    return review;
  }

  private refill(): void {
    if (this.refilling || this.queue().length >= REFILL_BELOW) {
      return;
    }
    this.refilling = true;
    this.api.pending(BATCH_SIZE).subscribe({
      next: (page) => {
        this.totalPending.set(page.totalPending);
        const known = new Set(this.queue().map((item) => item.hintId));
        const fresh = page.items.filter(
          (item) => !known.has(item.hintId) && !this.skipped.has(item.hintId),
        );
        this.queue.update((queue) => [...queue, ...fresh]);
        this.refilling = false;
      },
      error: () => (this.refilling = false),
    });
  }
}

function errorMessage(error: HttpErrorResponse): string {
  const code = (error.error as ApiErrorResponse | null)?.errorCode;
  if (code && ERROR_MESSAGES[code]) {
    return ERROR_MESSAGES[code];
  }
  if (error.status === 404) {
    return 'This hint no longer exists.';
  }
  return GENERIC_ERROR;
}
