import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Auth } from '../auth';

@Component({
  imports: [ReactiveFormsModule, RouterLink],
  selector: 'app-forgot-password',
  templateUrl: './forgot-password.html',
})
export class ForgotPassword {
  private readonly auth = inject(Auth);

  readonly form = new FormGroup({
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
  });

  readonly submitting = signal(false);
  readonly sentTo = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { email } = this.form.getRawValue();
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.auth.forgotPassword(email).subscribe({
      next: () => this.sentTo.set(email),
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        this.errorMessage.set(
          error.status === 429
            ? 'Too many attempts. Please wait a minute and try again.'
            : 'Something went wrong. Please try again.',
        );
      },
    });
  }
}
