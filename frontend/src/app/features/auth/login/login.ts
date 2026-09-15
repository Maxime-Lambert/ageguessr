import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Auth } from '../auth';
import { ApiErrorResponse } from '../models';

@Component({
  imports: [ReactiveFormsModule, RouterLink],
  selector: 'app-login',
  templateUrl: './login.html',
})
export class Login {
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);

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
    this.auth.login(email, password).subscribe({
      next: () => this.router.navigate(['/']),
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        const body = error.error as ApiErrorResponse | null;
        if (error.status === 403 && body?.errorCode === 'EMAIL_NOT_VERIFIED') {
          this.needsVerification.set(true);
          return;
        }
        this.errorMessage.set('Invalid email or password.');
      },
    });
  }

  resendVerification(): void {
    const email = this.form.controls.email.value;
    this.auth.resendVerification(email).subscribe(() => this.resendSent.set(true));
  }
}
