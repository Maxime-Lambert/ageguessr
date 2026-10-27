import { ApiErrorResponse } from '../../shared/api-error';
import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

/**
 * Mirrors the backend's @Password: at least 12 characters, no composition rule. The
 * common-password list is only checked by the backend (see passwordRefusalMessage).
 * Upper bound: bcrypt only accepts 72 bytes, counted in UTF-8, so an
 * accented letter counts for 2 and an emoji for 4. The backend stays the source of truth.
 */
export const PASSWORD_MIN_LENGTH = 12;
export const PASSWORD_MAX_BYTES = 72;

export const maxUtf8Bytes =
  (max: number): ValidatorFn =>
  (control: AbstractControl): ValidationErrors | null =>
    new TextEncoder().encode(control.value ?? '').length > max ? { maxUtf8Bytes: { max } } : null;

/** Validators for a password being set (register, reset, change) - not for login. */
export const NEW_PASSWORD_VALIDATORS = [
  Validators.required,
  Validators.minLength(PASSWORD_MIN_LENGTH),
  maxUtf8Bytes(PASSWORD_MAX_BYTES),
];

export const PASSWORD_TOO_LONG_MESSAGE =
  'Too long: 72 characters at most (accented letters and emoji count for more).';

export const PASSWORD_HINT = 'At least 12 characters. Avoid common passwords.';

const REFUSAL_MESSAGES: Record<string, string> = {
  PASSWORD_TOO_COMMON:
    'This password is too common. Try a short phrase, like three or four random words.',
  PASSWORD_CONTAINS_PERSONAL_INFO: "Your password can't contain your username or email.",
};

/** The message for a new password refused by the backend's policy, or null for any other error. */
export function passwordRefusalMessage(error: HttpErrorResponse): string | null {
  if (error.status !== 400) {
    return null;
  }
  const errorCode = (error.error as ApiErrorResponse | null)?.errorCode;
  return (errorCode && REFUSAL_MESSAGES[errorCode]) ?? null;
}
