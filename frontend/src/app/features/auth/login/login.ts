import { ApiErrorResponse } from '../../../shared/api-error';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable, catchError, of, switchMap } from 'rxjs';
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

  /** Set once the password is right and the account has two-factor: the code is due. */
  readonly challenge = signal<string | null>(null);
  readonly codeForm = new FormGroup({
    code: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^\d{6}$/)],
    }),
  });
  readonly recoverySent = signal(false);

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { email, password } = this.form.getRawValue();
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.needsVerification.set(false);
    this.auth.login(email, password).subscribe({
      next: (result) => {
        if ('challenge' in result) {
          this.submitting.set(false);
          this.challenge.set(result.challenge);
          return;
        }
        this.finishSignIn(of(result.user));
      },
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

  submitCode(): void {
    const challenge = this.challenge();
    if (!challenge || this.codeForm.invalid) {
      this.codeForm.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.finishSignIn(this.auth.loginWithCode(challenge, this.codeForm.getRawValue().code), true);
  }

  /** The app is lost: an email with a link that turns two-factor off. */
  requestRecovery(): void {
    const challenge = this.challenge();
    if (challenge) {
      this.auth.requestTwoFactorRecovery(challenge).subscribe({
        next: () => this.recoverySent.set(true),
        error: () => this.backToPassword(),
      });
    }
  }

  /**
   * Signed in: the guest games of this browser join the account, then back to where the
   * player was. A failed claim never blocks the login: the next login tries again.
   */
  private finishSignIn(signedIn: Observable<unknown>, withCode = false): void {
    signedIn
      .pipe(switchMap(() => this.game.claimGuestPlays().pipe(catchError(() => of(null)))))
      .subscribe({
        next: () => this.router.navigateByUrl(this.safeReturnUrl()),
        error: (error: HttpErrorResponse) => {
          this.submitting.set(false);
          if (!withCode) {
            this.errorMessage.set($localize`:@@login.invalid:Invalid email or password.`);
          } else if (error.status === 404) {
            // The challenge expired (5 minutes) or was used: the password again.
            this.backToPassword();
          } else {
            this.codeForm.reset();
            this.errorMessage.set(
              $localize`:@@login.invalidCode:Incorrect or expired code. After too many attempts, your account is locked for 15 minutes.`,
            );
          }
        },
      });
  }

  private backToPassword(): void {
    this.challenge.set(null);
    this.codeForm.reset();
    this.form.controls.password.reset();
    this.errorMessage.set(
      $localize`:@@login.challengeExpired:This sign-in has expired. Please enter your password again.`,
    );
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
