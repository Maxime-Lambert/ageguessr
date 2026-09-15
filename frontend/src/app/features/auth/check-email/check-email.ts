import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Auth } from '../auth';

@Component({
  imports: [],
  selector: 'app-check-email',
  templateUrl: './check-email.html',
})
export class CheckEmail {
  private readonly auth = inject(Auth);
  private readonly route = inject(ActivatedRoute);

  readonly email = this.route.snapshot.queryParamMap.get('email') ?? '';
  readonly resendSent = signal(false);

  resend(): void {
    this.auth.resendVerification(this.email).subscribe(() => this.resendSent.set(true));
  }
}
