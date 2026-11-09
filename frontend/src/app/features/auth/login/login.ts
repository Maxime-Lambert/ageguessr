import { ApiErrorResponse } from '../../../shared/api-error';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, of, switchMap } from 'rxjs';
import { GameApi } from '../../game/game-api';
import { Auth } from '../auth';
import { Page } from '../../../shared/ui/page/page';
import { Field, TextInput } from '../../../shared/ui/field/field';
import { Button } from '../../../shared/ui/button/button';

@Component({
  imports: [ReactiveFormsModule, RouterLink, Page, Field, TextInput, Button],
  selector: 'app-login',
  templateUrl: './login.html',
})
export class Login {
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly game = inject(GameApi);

  readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly needsVerification = signal(false);
  readonly resendSent = signal(false);

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { email, password } = this.form.getRawValue();
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.needsVerification.set(false);
    this.auth
      .login(email, password)
      .pipe(
        // The guest games of this browser join the account. A failure never blocks the
        // login: the games stay on the guest, and the next login tries again.
        switchMap(() => this.game.claimGuestPlays().pipe(catchError(() => of(null)))),
      )
      .subscribe({
        next: () => this.router.navigateByUrl(this.safeReturnUrl()),
        error: (error: HttpErrorResponse) => {
          this.submitting.set(false);
          const body = error.error as ApiErrorResponse | null;
          if (error.status === 403 && body?.errorCode === 'EMAIL_NOT_VERIFIED') {
            this.needsVerification.set(true);
            return;
          }
          this.errorMessage.set($localize`:@@login.invalid:Invalid email or password.`);
        },
      });
  }

  /** Only same-site paths: "//evil.com" or "/\evil.com" would be an open redirect. */
  private safeReturnUrl(): string {
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
    return returnUrl && /^\/(?![/\\])/.test(returnUrl) ? returnUrl : '/';
  }

  resendVerification(): void {
    const email = this.form.controls.email.value;
    this.auth.resendVerification(email).subscribe(() => this.resendSent.set(true));
  }
}
