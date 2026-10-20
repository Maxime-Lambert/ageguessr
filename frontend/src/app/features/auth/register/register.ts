import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Auth } from '../auth';
import { ApiErrorResponse, USERNAME_PATTERN } from '../models';
import { NEW_PASSWORD_VALIDATORS, PASSWORD_TOO_LONG_MESSAGE } from '../password-rules';

@Component({
  imports: [ReactiveFormsModule, RouterLink],
  selector: 'app-register',
  templateUrl: './register.html',
})
export class Register {
  private readonly auth = inject(Auth);
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
  });

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly usernameTaken = signal(false);
  readonly passwordTooLongMessage = PASSWORD_TOO_LONG_MESSAGE;

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { email, username, password, confirmPassword } = this.form.getRawValue();
    if (password !== confirmPassword) {
      this.errorMessage.set("Passwords don't match.");
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);
    this.usernameTaken.set(false);
    this.auth.register(email, username, password).subscribe({
      next: () => this.router.navigate(['/check-email'], { queryParams: { email } }),
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        const body = error.error as ApiErrorResponse | null;
        if (error.status === 409 && body?.errorCode === 'USERNAME_TAKEN') {
          this.usernameTaken.set(true);
          return;
        }
        this.errorMessage.set(
          error.status === 409
            ? 'An account with this email already exists.'
            : 'Something went wrong. Please try again.',
        );
      },
    });
  }
}
