import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, output, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { CurationApi } from '../curation-api';
import { PersonSearchResult, displayName } from '../models';

/**
 * Handles a removal request received by email: find the person by name or QID, then
 * exclude them. Two clicks, on purpose: excluding erases every photo of the person and
 * cannot be undone from the interface.
 */
@Component({
  imports: [ReactiveFormsModule],
  selector: 'app-exclusion',
  templateUrl: './exclusion.html',
})
export class Exclusion {
  private readonly api = inject(CurationApi);

  readonly excluded = output<string>();

  // A FormGroup bound with [formGroup]: without it, (ngSubmit) never fires with
  // ReactiveFormsModule alone, and the search button would do nothing.
  readonly form = new FormGroup({
    query: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(2), Validators.maxLength(100)],
    }),
  });
  readonly query = this.form.controls.query;
  readonly results = signal<PersonSearchResult[] | null>(null);
  readonly confirming = signal<string | null>(null);
  readonly busy = signal(false);
  readonly message = signal<string | null>(null);
  readonly error = signal<string | null>(null);

  protected readonly displayName = displayName;

  search(): void {
    const text = this.query.value.trim();
    if (text.length < 2 || text.length > 100) {
      this.query.markAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.message.set(null);
    this.confirming.set(null);
    this.api.searchPeople(text).subscribe({
      next: (people) => {
        this.results.set(people);
        this.busy.set(false);
      },
      error: () => {
        this.error.set('The search failed. Please try again.');
        this.busy.set(false);
      },
    });
  }

  confirm(person: PersonSearchResult): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.exclude(person.id).subscribe({
      next: (result) => {
        this.results.update((people) =>
          (people ?? []).map((p) =>
            p.id === person.id ? { ...p, excluded: true, photoCount: 0 } : p,
          ),
        );
        this.confirming.set(null);
        this.message.set(
          `${displayName(person)} is excluded: ${result.deletedPhotos} photo(s) deleted. Confirm it to the person who asked.`,
        );
        this.busy.set(false);
        this.excluded.emit(person.id);
      },
      error: (error: HttpErrorResponse) => {
        this.error.set(
          error.status === 404
            ? 'This person no longer exists.'
            : 'The exclusion failed. Please try again.',
        );
        this.busy.set(false);
      },
    });
  }
}
