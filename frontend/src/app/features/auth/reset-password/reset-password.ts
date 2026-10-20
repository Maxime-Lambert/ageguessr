import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Auth } from '../auth';
import { NEW_PASSWORD_VALIDATORS, PASSWORD_TOO_LONG_MESSAGE } from '../password-rules';

type ResetStatus = 'form' | 'success' | 'invalid-link';

@Component({
  imports: [ReactiveFormsModule, RouterLink],
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

  readonly passwordTooLongMessage = PASSWORD_TOO_LONG_MESSAGE;
  readonly status = signal<ResetStatus>(this.token ? 'form' : 'invalid-link');
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  submit(): void {
    if (this.form.invalid || !this.token) {
      this.form.markAllAsTouched();
      return;
    }

    const { newPassword, confirmPassword } = this.form.getRawValue();
    if (newPassword !== confirmPassword) {
      this.errorMessage.set("Passwords don't match.");
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);
    this.auth.resetPassword(this.token, newPassword).subscribe({
      next: () => this.status.set('success'),
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        if (error.status === 404) {
          this.status.set('invalid-link');
          return;
        }
        this.errorMessage.set('Something went wrong. Please try again.');
      },
    });
  }
}
