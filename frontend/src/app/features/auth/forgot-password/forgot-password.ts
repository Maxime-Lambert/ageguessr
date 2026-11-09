import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Auth } from '../auth';
import { COMMON_MESSAGES } from '../../../shared/common-messages';
import { Page } from '../../../shared/ui/page/page';
import { Field, TextInput } from '../../../shared/ui/field/field';
import { Button } from '../../../shared/ui/button/button';

@Component({
  imports: [ReactiveFormsModule, RouterLink, Page, Field, TextInput, Button],
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
      next: () => {
        this.submitting.set(false);
        this.sentTo.set(email);
      },
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        this.errorMessage.set(
          error.status === 429 ? COMMON_MESSAGES.tooManyAttempts : COMMON_MESSAGES.genericError,
        );
      },
    });
  }
}
