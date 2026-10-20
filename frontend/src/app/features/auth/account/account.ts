import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Auth } from '../auth';
import { ApiErrorResponse, USERNAME_PATTERN } from '../models';
import {
  NEW_PASSWORD_VALIDATORS,
  PASSWORD_HINT,
  PASSWORD_TOO_LONG_MESSAGE,
  passwordRefusalMessage,
} from '../password-rules';

const WRONG_PASSWORD_MESSAGE =
  'Incorrect password. After too many attempts, your account is locked for 15 minutes.';
const GENERIC_ERROR_MESSAGE = 'Something went wrong. Please try again.';

function isWrongPassword(error: HttpErrorResponse): boolean {
  return (
    error.status === 403 &&
    (error.error as ApiErrorResponse | null)?.errorCode === 'INVALID_CURRENT_PASSWORD'
  );
}

@Component({
  imports: [ReactiveFormsModule],
  selector: 'app-account',
  templateUrl: './account.html',
})
export class Account {
  protected readonly auth = inject(Auth);

  readonly usernameForm = new FormGroup({
    username: new FormControl(this.auth.currentUser()?.username ?? '', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(USERNAME_PATTERN)],
    }),
  });
  readonly usernameStatus = signal<'idle' | 'saving' | 'saved'>('idle');
  readonly usernameError = signal<string | null>(null);

  readonly passwordForm = new FormGroup({
    currentPassword: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    newPassword: new FormControl('', { nonNullable: true, validators: NEW_PASSWORD_VALIDATORS }),
    confirmPassword: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });
  readonly passwordTooLongMessage = PASSWORD_TOO_LONG_MESSAGE;
  readonly passwordHint = PASSWORD_HINT;
  readonly passwordRefusal = signal<string | null>(null);
  readonly passwordStatus = signal<'idle' | 'saving' | 'saved'>('idle');
  readonly passwordError = signal<string | null>(null);

  readonly deletionForm = new FormGroup({
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });
  readonly deletionStatus = signal<'idle' | 'sending' | 'sent'>('idle');
  readonly deletionError = signal<string | null>(null);

  saveUsername(): void {
    if (this.usernameForm.invalid) {
      this.usernameForm.markAllAsTouched();
      return;
    }

    this.usernameStatus.set('saving');
    this.usernameError.set(null);
    this.auth.changeUsername(this.usernameForm.getRawValue().username).subscribe({
      next: () => this.usernameStatus.set('saved'),
      error: (error: HttpErrorResponse) => {
        this.usernameStatus.set('idle');
        const body = error.error as ApiErrorResponse | null;
        this.usernameError.set(
          error.status === 409 && body?.errorCode === 'USERNAME_TAKEN'
            ? 'This username is already taken.'
            : GENERIC_ERROR_MESSAGE,
        );
      },
    });
  }

  changePassword(): void {
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }

    const { currentPassword, newPassword, confirmPassword } = this.passwordForm.getRawValue();
    if (newPassword !== confirmPassword) {
      this.passwordError.set("New passwords don't match.");
      return;
    }

    this.passwordStatus.set('saving');
    this.passwordError.set(null);
    this.passwordRefusal.set(null);
    this.auth.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        this.passwordStatus.set('saved');
        this.passwordForm.reset();
      },
      error: (error: HttpErrorResponse) => {
        this.passwordStatus.set('idle');
        const refusal = passwordRefusalMessage(error);
        if (refusal) {
          this.passwordRefusal.set(refusal);
          return;
        }
        this.passwordError.set(
          isWrongPassword(error) ? WRONG_PASSWORD_MESSAGE : GENERIC_ERROR_MESSAGE,
        );
      },
    });
  }

  requestDeletion(): void {
    if (this.deletionForm.invalid) {
      this.deletionForm.markAllAsTouched();
      return;
    }

    this.deletionStatus.set('sending');
    this.deletionError.set(null);
    this.auth.requestAccountDeletion(this.deletionForm.getRawValue().password).subscribe({
      next: () => {
        this.deletionStatus.set('sent');
        this.deletionForm.reset();
      },
      error: (error: HttpErrorResponse) => {
        this.deletionStatus.set('idle');
        this.deletionError.set(
          isWrongPassword(error) ? WRONG_PASSWORD_MESSAGE : GENERIC_ERROR_MESSAGE,
        );
      },
    });
  }
}
