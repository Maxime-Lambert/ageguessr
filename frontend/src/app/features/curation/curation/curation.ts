import { ApiErrorResponse } from '../../../shared/api-error';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { CurationApi } from '../curation-api';
import { Exclusion } from '../exclusion/exclusion';
import { PendingPhoto, PhotoDateSource, ReviewDecision, displayName } from '../models';

/** How many photos to ask for at once, and when to ask for more. */
const BATCH_SIZE = 20;
const REFILL_BELOW = 5;
/** The current photo and the next one are loaded ahead, so the next decision has no wait. */
const PRELOADED = 2;

const DATE_SOURCE_LABELS: Record<PhotoDateSource, string> = {
  STRUCTURED_DATA: 'Commons structured data',
  INFORMATION_TEMPLATE: 'Commons description',
  BOTH: 'Commons structured data and description',
};

const GENERIC_ERROR = 'Something went wrong. Please try again.';

/**
 * Mirrors app.people.ingest.min-image-height (ageguessr-api): below this a photo is too
 * small to show. The ingestion refuses them; the warning catches any that slipped in.
 */
export const MIN_IMAGE_HEIGHT = 600;

/**
 * Photo curation, one photo at a time. A photo enters the game only if exactly one person
 * is visible, framed at least from the face to the full body (plans/done/photo-curation.md).
 */
@Component({
  imports: [Exclusion],
  selector: 'app-curation',
  templateUrl: './curation.html',
  host: { '(document:keydown)': 'onKeydown($event)' },
})
export class Curation implements OnDestroy {
  private readonly api = inject(CurationApi);

  readonly queue = signal<PendingPhoto[]>([]);
  readonly totalPending = signal(0);
  readonly current = computed(() => this.queue()[0] ?? null);
  readonly status = signal<'loading' | 'ready' | 'error'>('loading');
  readonly busy = signal(false);
  readonly message = signal<string | null>(null);
  readonly lastAction = signal<{ item: PendingPhoto; decision: ReviewDecision } | null>(null);
  /** photoId -> blob: URL of the loaded image, or 'error'. */
  readonly images = signal<Record<string, string>>({});
  /** Real size of the shown image, read by the browser once it has loaded. */
  readonly imageSize = signal<{ photoId: string; width: number; height: number } | null>(null);
  protected readonly minImageHeight = MIN_IMAGE_HEIGHT;

  protected readonly displayName = displayName;

  onImageLoad(photoId: string, event: Event): void {
    const image = event.target as HTMLImageElement;
    this.imageSize.set({ photoId, width: image.naturalWidth, height: image.naturalHeight });
  }

  /** The size of the current photo's image, once loaded; null before. */
  sizeOf(photoId: string): { width: number; height: number } | null {
    const size = this.imageSize();
    return size?.photoId === photoId ? size : null;
  }

  /** Skipped this session: not shown again until the page is reloaded. */
  private readonly skipped = new Set<string>();
  private readonly loadingImages = new Set<string>();
  private refilling = false;

  constructor() {
    this.reload();
  }

  ngOnDestroy(): void {
    Object.keys(this.images()).forEach((photoId) => this.release(photoId));
  }

  reload(): void {
    Object.keys(this.images()).forEach((photoId) => this.release(photoId));
    this.queue.set([]);
    this.status.set('loading');
    this.api.pending(BATCH_SIZE).subscribe({
      next: (page) => {
        this.totalPending.set(page.totalPending);
        this.queue.set(page.items.filter((item) => !this.skipped.has(item.photoId)));
        this.status.set('ready');
        this.syncImages();
      },
      error: () => this.status.set('error'),
    });
  }

  decide(decision: ReviewDecision): void {
    const item = this.current();
    if (!item || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.message.set(null);
    this.api.review(item.photoId, decision).subscribe({
      next: () => {
        this.lastAction.set({ item, decision });
        this.totalPending.update((n) => Math.max(0, n - 1));
        this.dropCurrent();
        this.busy.set(false);
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
    this.skipped.add(item.photoId);
    this.dropCurrent();
  }

  /** Back to PENDING, and back on top of the queue. */
  undo(): void {
    const last = this.lastAction();
    if (!last || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.message.set(null);
    this.api.review(last.item.photoId, 'PENDING').subscribe({
      next: () => {
        this.lastAction.set(null);
        this.queue.update((queue) => [last.item, ...queue]);
        this.totalPending.update((n) => n + 1);
        this.syncImages();
        this.busy.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.message.set(errorMessage(error));
        this.busy.set(false);
      },
    });
  }

  /** A person was just excluded: their photos are gone, start over from the server. */
  onExcluded(): void {
    this.lastAction.set(null);
    this.reload();
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.ctrlKey || event.metaKey || event.altKey) {
      return;
    }
    // Typing in the search field must never approve or reject a photo.
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

  photoDate(item: PendingPhoto): string {
    if (item.takenFrom === item.takenTo) {
      return formatDate(item.takenFrom, { day: 'numeric', month: 'long', year: 'numeric' });
    }
    if (item.takenFrom.slice(0, 7) === item.takenTo.slice(0, 7)) {
      return formatDate(item.takenFrom, { month: 'long', year: 'numeric' });
    }
    return item.takenFrom.slice(0, 4);
  }

  fullDate(isoDate: string): string {
    return formatDate(isoDate, { day: 'numeric', month: 'long', year: 'numeric' });
  }

  dateSourceLabel(source: PhotoDateSource): string {
    return DATE_SOURCE_LABELS[source];
  }

  private dropCurrent(): void {
    this.imageSize.set(null);
    const [dropped, ...rest] = this.queue();
    if (dropped) {
      this.release(dropped.photoId);
    }
    this.queue.set(rest);
    this.syncImages();
    this.refill();
  }

  private refill(): void {
    if (this.refilling || this.queue().length >= REFILL_BELOW) {
      return;
    }
    this.refilling = true;
    this.api.pending(BATCH_SIZE).subscribe({
      next: (page) => {
        this.totalPending.set(page.totalPending);
        const known = new Set(this.queue().map((item) => item.photoId));
        const fresh = page.items.filter(
          (item) => !known.has(item.photoId) && !this.skipped.has(item.photoId),
        );
        this.queue.update((queue) => [...queue, ...fresh]);
        this.syncImages();
        this.refilling = false;
      },
      error: () => (this.refilling = false),
    });
  }

  private syncImages(): void {
    this.queue()
      .slice(0, PRELOADED)
      .forEach((item) => this.load(item.photoId));
  }

  private load(photoId: string): void {
    if (this.images()[photoId] || this.loadingImages.has(photoId)) {
      return;
    }
    this.loadingImages.add(photoId);
    this.api.image(photoId).subscribe({
      next: (blob) => {
        this.loadingImages.delete(photoId);
        // Dropped meanwhile (decided or skipped fast): do not keep a URL nobody frees.
        if (this.queue().some((item) => item.photoId === photoId)) {
          this.images.update((images) => ({ ...images, [photoId]: URL.createObjectURL(blob) }));
        }
      },
      error: () => {
        this.loadingImages.delete(photoId);
        this.images.update((images) => ({ ...images, [photoId]: 'error' }));
      },
    });
  }

  /** blob: URLs hold the image in memory until revoked. */
  private release(photoId: string): void {
    const url = this.images()[photoId];
    if (url && url !== 'error') {
      URL.revokeObjectURL(url);
    }
    this.images.update((images) => {
      const rest = { ...images };
      delete rest[photoId];
      return rest;
    });
  }
}

function errorMessage(error: HttpErrorResponse): string {
  const code = (error.error as ApiErrorResponse | null)?.errorCode;
  if (code === 'PHOTO_SLOT_TAKEN') {
    return 'Another photo of this person already has this age or year.';
  }
  if (error.status === 404) {
    return 'This photo no longer exists: the person may have been excluded.';
  }
  return GENERIC_ERROR;
}

function formatDate(isoDate: string, options: Intl.DateTimeFormatOptions): string {
  return new Intl.DateTimeFormat('en-GB', { ...options, timeZone: 'UTC' }).format(
    new Date(`${isoDate}T00:00:00Z`),
  );
}
