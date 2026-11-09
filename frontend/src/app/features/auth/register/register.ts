import { ApiErrorResponse } from '../../../shared/api-error';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Auth } from '../auth';
import { toApi } from '../../../shared/i18n/language';
import { SiteLanguageService } from '../../i18n/site-language';
import { USERNAME_PATTERN, USERNAME_RULE_MESSAGE, USERNAME_TAKEN_MESSAGE } from '../models';
import {
  NEW_PASSWORD_VALIDATORS,
  PASSWORDS_DIFFER_MESSAGE,
  PASSWORD_HINT,
  newPasswordError,
  passwordRefusalMessage,
} from '../password-rules';
import { COMMON_MESSAGES } from '../../../shared/common-messages';
import { Button } from '../../../shared/ui/button/button';
import { Field, TextInput } from '../../../shared/ui/field/field';
import { Page } from '../../../shared/ui/page/page';

@Component({
  imports: [ReactiveFormsModule, RouterLink, Page, Field, TextInput, Button],
  selector: 'app-register',
  templateUrl: './register.html',
})
export class Register {
  private readonly auth = inject(Auth);
  private readonly language = inject(SiteLanguageService);
  private readonly router = inject(Router);

  readonly form = new FormGroup({
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
    username: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(USERNAME_PATTERN)],
    }),
    password: new FormControl('', { nonNullable: true, validators: NEW_PASSWORD_VALIDATORS }),
    confirmPassword: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    // Never pre-checked: acceptance must be an explicit action (the API refuses false too).
    acceptTerms: new FormControl(false, {
      nonNullable: true,
      validators: [Validators.requiredTrue],
    }),
  });

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly usernameTaken = signal(false);
  readonly passwordHint = PASSWORD_HINT;
  readonly passwordRefusal = signal<string | null>(null);
  readonly usernameHint = $localize`:@@register.usernameHint:Shown to your friends. You can change it later.`;

  usernameError(): string | null {
    if (this.usernameTaken()) {
      return USERNAME_TAKEN_MESSAGE;
    }
    const control = this.form.controls.username;
    return control.invalid && control.touched ? USERNAME_RULE_MESSAGE : null;
  }

  passwordError(): string | null {
    return newPasswordError(this.form.controls.password, this.passwordRefusal());
  }

  termsMissing(): boolean {
    const control = this.form.controls.acceptTerms;
    return control.invalid && control.touched;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { email, username, password, confirmPassword, acceptTerms } = this.form.getRawValue();
    if (password !== confirmPassword) {
      this.errorMessage.set(PASSWORDS_DIFFER_MESSAGE);
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);
    this.usernameTaken.set(false);
    this.passwordRefusal.set(null);
    this.auth
      .register(email, username, password, acceptTerms, toApi(this.language.current))
      .subscribe({
        next: () => this.router.navigate(['/check-email'], { queryParams: { email } }),
        error: (error: HttpErrorResponse) => {
          this.submitting.set(false);
          const body = error.error as ApiErrorResponse | null;
          if (error.status === 409 && body?.errorCode === 'USERNAME_TAKEN') {
            this.usernameTaken.set(true);
            return;
          }
          const refusal = passwordRefusalMessage(error);
          if (refusal) {
            this.passwordRefusal.set(refusal);
            return;
          }
          this.errorMessage.set(
            error.status === 409
              ? $localize`:@@register.emailTaken:An account with this email already exists.`
              : COMMON_MESSAGES.genericError,
          );
        },
      });
  }
}
