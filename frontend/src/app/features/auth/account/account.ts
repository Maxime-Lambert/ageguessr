import { ApiErrorResponse } from '../../../shared/api-error';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Auth } from '../auth';
import { FILE_SAVER, filenameOf } from '../file-saver';
import QRCode from 'qrcode';
import { USERNAME_PATTERN, USERNAME_RULE_MESSAGE, USERNAME_TAKEN_MESSAGE } from '../models';
import {
  NEW_PASSWORD_VALIDATORS,
  PASSWORD_HINT,
  newPasswordError,
  passwordRefusalMessage,
} from '../password-rules';
import { COMMON_MESSAGES } from '../../../shared/common-messages';
import { Page } from '../../../shared/ui/page/page';
import { Field, TextInput } from '../../../shared/ui/field/field';
import { Button } from '../../../shared/ui/button/button';

const WRONG_PASSWORD_MESSAGE = $localize`:@@account.wrongPassword:Incorrect password. After too many attempts, your account is locked for 15 minutes.`;
const GENERIC_ERROR_MESSAGE = COMMON_MESSAGES.genericError;

const WRONG_CODE_MESSAGE = $localize`:@@account.wrongCode:Incorrect or expired code. After too many attempts, your account is locked for 15 minutes.`;

function isWrongCode(error: HttpErrorResponse): boolean {
  return (
    error.status === 403 &&
    (error.error as ApiErrorResponse | null)?.errorCode === 'INVALID_TOTP_CODE'
  );
}

/** Why a two-factor step was refused: the password, the code, or something else. */
function twoFactorError(error: HttpErrorResponse): string {
  if (isWrongPassword(error)) {
    return WRONG_PASSWORD_MESSAGE;
  }
  return isWrongCode(error) ? WRONG_CODE_MESSAGE : GENERIC_ERROR_MESSAGE;
}

function isWrongPassword(error: HttpErrorResponse): boolean {
  return (
    error.status === 403 &&
    (error.error as ApiErrorResponse | null)?.errorCode === 'INVALID_CURRENT_PASSWORD'
  );
}

@Component({
  imports: [ReactiveFormsModule, Page, Field, TextInput, Button],
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
  readonly passwordHint = PASSWORD_HINT;
  readonly passwordRefusal = signal<string | null>(null);
  readonly passwordStatus = signal<'idle' | 'saving' | 'saved'>('idle');
  readonly passwordError = signal<string | null>(null);

  private readonly fileSaver = inject(FILE_SAVER);
  readonly dataStatus = signal<'idle' | 'downloading'>('idle');
  readonly dataError = signal<string | null>(null);

  /** Turning two-factor on: the password, then the QR code and a first code. */
  readonly twoFactorPasswordForm = new FormGroup({
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });
  readonly twoFactorCodeForm = new FormGroup({
    code: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^\d{6}$/)],
    }),
  });
  /** Turning it off: the password and a code, so an open session is not enough. */
  readonly twoFactorOffForm = new FormGroup({
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    code: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^\d{6}$/)],
    }),
  });
  /** Set between the setup and the first code: shown once, never stored by the page. */
  readonly twoFactorSetup = signal<{ secret: string; qrCode: string } | null>(null);
  readonly twoFactorBusy = signal(false);
  readonly twoFactorError = signal<string | null>(null);
  readonly twoFactorStatus = signal<'idle' | 'enabled' | 'disabled'>('idle');

  readonly deletionForm = new FormGroup({
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });
  readonly deletionStatus = signal<'idle' | 'sending' | 'sent'>('idle');
  readonly deletionError = signal<string | null>(null);

  usernameFieldError(): string | null {
    const control = this.usernameForm.controls.username;
    return control.invalid && control.touched ? USERNAME_RULE_MESSAGE : null;
  }

  newPasswordFieldError(): string | null {
    return newPasswordError(this.passwordForm.controls.newPassword, this.passwordRefusal());
  }

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
            ? USERNAME_TAKEN_MESSAGE
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
      this.passwordError.set($localize`:@@account.passwordsDiffer:New passwords don't match.`);
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

  downloadData(): void {
    this.dataStatus.set('downloading');
    this.dataError.set(null);
    this.auth.downloadData().subscribe({
      next: (response) => {
        this.dataStatus.set('idle');
        const filename = filenameOf(
          response.headers.get('Content-Disposition'),
          'ageguessr-data.json',
        );
        this.fileSaver.save(response.body!, filename);
      },
      error: () => {
        this.dataStatus.set('idle');
        this.dataError.set(GENERIC_ERROR_MESSAGE);
      },
    });
  }

  startTwoFactor(): void {
    if (this.twoFactorPasswordForm.invalid) {
      this.twoFactorPasswordForm.markAllAsTouched();
      return;
    }
    this.twoFactorBusy.set(true);
    this.twoFactorError.set(null);
    this.auth.setUpTwoFactor(this.twoFactorPasswordForm.getRawValue().password).subscribe({
      next: async (setup) => {
        const qrCode = await QRCode.toDataURL(setup.otpauthUri, { margin: 1, width: 192 });
        this.twoFactorPasswordForm.reset();
        this.twoFactorSetup.set({ secret: setup.secret, qrCode });
        this.twoFactorBusy.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.twoFactorBusy.set(false);
        this.twoFactorError.set(twoFactorError(error));
      },
    });
  }

  enableTwoFactor(): void {
    if (this.twoFactorCodeForm.invalid) {
      this.twoFactorCodeForm.markAllAsTouched();
      return;
    }
    this.twoFactorBusy.set(true);
    this.twoFactorError.set(null);
    this.auth.enableTwoFactor(this.twoFactorCodeForm.getRawValue().code).subscribe({
      next: () => {
        this.twoFactorBusy.set(false);
        this.twoFactorSetup.set(null);
        this.twoFactorCodeForm.reset();
        this.twoFactorStatus.set('enabled');
      },
      error: (error: HttpErrorResponse) => {
        this.twoFactorBusy.set(false);
        this.twoFactorCodeForm.reset();
        this.twoFactorError.set(twoFactorError(error));
      },
    });
  }

  disableTwoFactor(): void {
    if (this.twoFactorOffForm.invalid) {
      this.twoFactorOffForm.markAllAsTouched();
      return;
    }
    const { password, code } = this.twoFactorOffForm.getRawValue();
    this.twoFactorBusy.set(true);
    this.twoFactorError.set(null);
    this.auth.disableTwoFactor(password, code).subscribe({
      next: () => {
        this.twoFactorBusy.set(false);
        this.twoFactorOffForm.reset();
        this.twoFactorStatus.set('disabled');
      },
      error: (error: HttpErrorResponse) => {
        this.twoFactorBusy.set(false);
        this.twoFactorOffForm.controls.code.reset();
        this.twoFactorError.set(twoFactorError(error));
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
