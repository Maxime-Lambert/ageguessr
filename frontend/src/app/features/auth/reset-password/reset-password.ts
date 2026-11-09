import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Auth } from '../auth';
import {
  NEW_PASSWORD_VALIDATORS,
  PASSWORDS_DIFFER_MESSAGE,
  PASSWORD_HINT,
  newPasswordError,
  passwordRefusalMessage,
} from '../password-rules';
import { COMMON_MESSAGES } from '../../../shared/common-messages';
import { Page } from '../../../shared/ui/page/page';
import { Field, TextInput } from '../../../shared/ui/field/field';
import { Button } from '../../../shared/ui/button/button';

type ResetStatus = 'form' | 'success' | 'invalid-link';

@Component({
  imports: [ReactiveFormsModule, RouterLink, Page, Field, TextInput, Button],
  selector: 'app-reset-password',
  templateUrl: './reset-password.html',
})
export class ResetPassword {
  private readonly auth = inject(Auth);
  private readonly token = inject(ActivatedRoute).snapshot.queryParamMap.get('token');

  readonly form = new FormGroup({
    newPassword: new FormControl('', { nonNullable: true, validators: NEW_PASSWORD_VALIDATORS }),
    confirmPassword: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  readonly passwordHint = PASSWORD_HINT;
  readonly passwordRefusal = signal<string | null>(null);
  readonly status = signal<ResetStatus>(this.token ? 'form' : 'invalid-link');
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  passwordError(): string | null {
    return newPasswordError(this.form.controls.newPassword, this.passwordRefusal());
  }

  submit(): void {
    if (this.form.invalid || !this.token) {
      this.form.markAllAsTouched();
      return;
    }

    const { newPassword, confirmPassword } = this.form.getRawValue();
    if (newPassword !== confirmPassword) {
      this.errorMessage.set(PASSWORDS_DIFFER_MESSAGE);
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);
    this.passwordRefusal.set(null);
    this.auth.resetPassword(this.token, newPassword).subscribe({
      next: () => this.status.set('success'),
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        if (error.status === 404) {
          this.status.set('invalid-link');
          return;
        }
        const refusal = passwordRefusalMessage(error);
        if (refusal) {
          this.passwordRefusal.set(refusal);
          return;
        }
        this.errorMessage.set(COMMON_MESSAGES.genericError);
      },
    });
  }
}
