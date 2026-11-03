import { Component, OnDestroy, inject, output, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { CurationApi } from '../curation-api';
import { PhotoReportReason, ReportedPhoto, displayName } from '../models';

/** The reasons as the player picked them (game-vocabulary), in English like the admin. */
export const PHOTO_REASON_LABELS: Record<PhotoReportReason, string> = {
  TOO_SMALL: 'Too small',
  NOT_IDENTIFIABLE: 'Person not identifiable',
  WRONG_PERSON: 'Wrong person',
  LOOKS_MINOR: 'Looks under 18',
  DISTURBING_PERSON: 'Person bothers the player',
  OTHER: 'Other',
};

/**
 * The photos players reported, most reported first, above the pending queue. Reject
 * takes the photo out of the game, Keep closes its reports, Exclude removes the person
 * (two clicks: it cannot be undone). Nothing is ever taken out without the admin.
 */
@Component({
  selector: 'app-reported-photos',
  templateUrl: './reported-photos.html',
})
export class ReportedPhotos implements OnDestroy {
  private readonly api = inject(CurationApi);

  /** A person was excluded from here: the pending queue may hold their photos. */
  readonly excluded = output<void>();

  readonly items = signal<ReportedPhoto[]>([]);
  readonly status = signal<'loading' | 'ready' | 'error'>('loading');
  readonly busy = signal<string | null>(null);
  readonly confirmingExclusion = signal<string | null>(null);
  readonly message = signal<string | null>(null);
  /** photoId -> blob: URL of the loaded image, or 'error'. */
  readonly images = signal<Record<string, string>>({});

  protected readonly displayName = displayName;

  constructor() {
    this.reload();
  }

  ngOnDestroy(): void {
    Object.values(this.images()).forEach((url) => this.release(url));
  }

  reload(): void {
    this.status.set('loading');
    this.api.reported().subscribe({
      next: (items) => {
        this.items.set(items);
        this.status.set('ready');
        items.forEach((item) => this.loadImage(item.photoId));
      },
      error: () => this.status.set('error'),
    });
  }

  /** "Wrong person (2), Other (1)", in the order of the list the player picks from. */
  reasonsText(item: ReportedPhoto): string {
    return (Object.keys(PHOTO_REASON_LABELS) as PhotoReportReason[])
      .filter((reason) => item.reasons[reason])
      .map((reason) => `${PHOTO_REASON_LABELS[reason]} (${item.reasons[reason]})`)
      .join(', ');
  }

  reject(item: ReportedPhoto): void {
    this.act(item, this.api.review(item.photoId, 'REJECTED'));
  }

  keep(item: ReportedPhoto): void {
    this.act(item, this.api.dismissReports(item.photoId));
  }

  /** First click asks, second click excludes. */
  exclude(item: ReportedPhoto): void {
    if (this.confirmingExclusion() !== item.photoId) {
      this.confirmingExclusion.set(item.photoId);
      return;
    }
    this.act(item, this.api.exclude(item.person.id), () => this.excluded.emit());
  }

  private act(item: ReportedPhoto, call: Observable<unknown>, done?: () => void): void {
    if (this.busy()) {
      return;
    }
    this.busy.set(item.photoId);
    this.message.set(null);
    call.subscribe({
      next: () => {
        this.busy.set(null);
        this.confirmingExclusion.set(null);
        this.drop(item.photoId);
        done?.();
      },
      error: () => {
        this.busy.set(null);
        this.message.set('Something went wrong. Please try again.');
      },
    });
  }

  private drop(photoId: string): void {
    this.items.update((items) => items.filter((item) => item.photoId !== photoId));
    const url = this.images()[photoId];
    if (url) {
      this.release(url);
      this.images.update((all) =>
        Object.fromEntries(Object.entries(all).filter(([id]) => id !== photoId)),
      );
    }
  }

  private loadImage(photoId: string): void {
    this.api.image(photoId).subscribe({
      next: (blob) =>
        this.images.update((all) => ({ ...all, [photoId]: URL.createObjectURL(blob) })),
      error: () => this.images.update((all) => ({ ...all, [photoId]: 'error' })),
    });
  }

  private release(url: string): void {
    if (url !== 'error') {
      URL.revokeObjectURL(url);
    }
  }
}
