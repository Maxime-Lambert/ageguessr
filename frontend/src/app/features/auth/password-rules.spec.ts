import { FormControl } from '@angular/forms';
import { maxUtf8Bytes } from './password-rules';

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
