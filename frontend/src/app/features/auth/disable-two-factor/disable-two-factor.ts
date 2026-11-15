import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Auth } from '../auth';
import { Page } from '../../../shared/ui/page/page';
import { Button } from '../../../shared/ui/button/button';

type RecoveryStatus = 'confirm' | 'disabling' | 'disabled' | 'invalid-link';

/**
 * The emailed link for a lost authenticator app. Never calls the API on load: link
 * scanners open the links of emails, which would turn two-factor off with no click.
 */
@Component({
  imports: [RouterLink, Page, Button],
  selector: 'app-disable-two-factor',
  templateUrl: './disable-two-factor.html',
})
export class DisableTwoFactor {
  private readonly auth = inject(Auth);
  private readonly token = inject(ActivatedRoute).snapshot.queryParamMap.get('token');

  readonly status = signal<RecoveryStatus>(this.token ? 'confirm' : 'invalid-link');

  confirm(): void {
    if (!this.token) {
      return;
    }
    this.status.set('disabling');
    this.auth.confirmTwoFactorRecovery(this.token).subscribe({
      next: () => this.status.set('disabled'),
      error: () => this.status.set('invalid-link'),
    });
  }
}
