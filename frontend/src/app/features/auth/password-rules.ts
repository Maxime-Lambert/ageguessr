import { AbstractControl, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

/**
 * Mirrors the backend's @Password: bcrypt only accepts 72 bytes, counted in UTF-8, so an
 * accented letter counts for 2 and an emoji for 4. The backend stays the source of truth.
 */
export const PASSWORD_MAX_BYTES = 72;

export const maxUtf8Bytes =
  (max: number): ValidatorFn =>
  (control: AbstractControl): ValidationErrors | null =>
    new TextEncoder().encode(control.value ?? '').length > max ? { maxUtf8Bytes: { max } } : null;

/** Validators for a password being set (register, reset, change) - not for login. */
export const NEW_PASSWORD_VALIDATORS = [
  Validators.required,
  Validators.minLength(8),
  maxUtf8Bytes(PASSWORD_MAX_BYTES),
];

export const PASSWORD_TOO_LONG_MESSAGE =
  'Too long: 72 characters at most (accented letters and emoji count for more).';
