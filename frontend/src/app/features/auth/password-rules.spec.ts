import { HttpErrorResponse } from '@angular/common/http';
import { FormControl } from '@angular/forms';
import { NEW_PASSWORD_VALIDATORS, maxUtf8Bytes, passwordRefusalMessage } from './password-rules';

describe('maxUtf8Bytes', () => {
  const validate = (value: string) => maxUtf8Bytes(72)(new FormControl(value));

  it('accepts exactly 72 bytes', () => {
    expect(validate('a'.repeat(72))).toBeNull();
  });

  it.each([
    ['73 ASCII characters', 'a'.repeat(73)],
    ['40 accented letters (80 bytes)', 'é'.repeat(40)],
    ['20 emoji (80 bytes)', '😀'.repeat(20)],
  ])('rejects %s', (_label, value) => {
    expect(validate(value)).toEqual({ maxUtf8Bytes: { max: 72 } });
  });
});

describe('NEW_PASSWORD_VALIDATORS', () => {
  const validate = (value: string) => new FormControl(value, NEW_PASSWORD_VALIDATORS).errors;

  it('requires at least 12 characters', () => {
    expect(validate('a'.repeat(11))).toHaveProperty('minlength');
    expect(validate('a'.repeat(12))).toBeNull();
  });
});

describe('passwordRefusalMessage', () => {
  const error = (status: number, errorCode?: string) =>
    new HttpErrorResponse({ status, error: errorCode ? { errorCode, message: '' } : null });

  it.each(['PASSWORD_TOO_COMMON', 'PASSWORD_CONTAINS_PERSONAL_INFO'])(
    'has a message for %s',
    (errorCode) => {
      expect(passwordRefusalMessage(error(400, errorCode))).toBeTruthy();
    },
  );

  it('ignores other errors', () => {
    expect(passwordRefusalMessage(error(400, 'VALIDATION_ERROR'))).toBeNull();
    expect(passwordRefusalMessage(error(409, 'PASSWORD_TOO_COMMON'))).toBeNull();
    expect(passwordRefusalMessage(error(500))).toBeNull();
  });
});
