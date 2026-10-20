import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Auth } from '../auth';

type DeletionStatus = 'confirm' | 'deleting' | 'deleted' | 'invalid-link';

/**
 * Never calls the API on load: mail providers' link scanners open links from emails
 * (some even run their JavaScript), which would delete the account with no click.
 */
@Component({
  imports: [RouterLink],
  selector: 'app-confirm-account-deletion',
  templateUrl: './confirm-account-deletion.html',
})
export class ConfirmAccountDeletion {
  private readonly auth = inject(Auth);
  private readonly token = inject(ActivatedRoute).snapshot.queryParamMap.get('token');

  readonly status = signal<DeletionStatus>(this.token ? 'confirm' : 'invalid-link');

  confirm(): void {
    if (!this.token) {
      return;
    }

    this.status.set('deleting');
    this.auth.confirmAccountDeletion(this.token).subscribe({
      next: () => this.status.set('deleted'),
      error: () => this.status.set('invalid-link'),
    });
  }
}
