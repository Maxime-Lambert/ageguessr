import { HttpErrorResponse } from '@angular/common/http';
import { COMMON_MESSAGES } from '../../shared/common-messages';
import { GONE, NO_SUCH_PLAYER, friendsError } from './friends-text';

const error = (status: number, errorCode?: string) =>
  new HttpErrorResponse({ status, error: errorCode ? { errorCode, message: 'x' } : null });

describe('friendsError', () => {
  it('says what a 404 means where it happened', () => {
    expect(friendsError(error(404))).toBe(GONE);
    expect(friendsError(error(404), NO_SUCH_PLAYER)).toBe(NO_SUCH_PLAYER);
  });

  it('reads a malformed username as nobody', () => {
    expect(friendsError(error(400, 'VALIDATION_ERROR'))).toBe(NO_SUCH_PLAYER);
  });

  it('translates every refusal of the API, never showing its own message', () => {
    expect(friendsError(error(409, 'ALREADY_FRIENDS'))).toBe('You are already friends.');
    expect(friendsError(error(409, 'REQUEST_ALREADY_SENT'))).toContain('already sent a request');
    expect(friendsError(error(409, 'INCOMING_REQUEST_EXISTS'))).toContain('requests received');
    expect(friendsError(error(409, 'FRIEND_LIMIT_REACHED'))).toContain('You reached');
    expect(friendsError(error(409, 'OTHER_FRIEND_LIMIT_REACHED'))).toContain('This player reached');
    expect(friendsError(error(409, 'PENDING_LIMIT_REACHED'))).toContain('Cancel some');
    expect(friendsError(error(409, 'SOMETHING_NEW'))).toBe(COMMON_MESSAGES.genericError);
    expect(friendsError(error(500))).toBe(COMMON_MESSAGES.genericError);
  });

  it('asks to wait when rate limited', () => {
    expect(friendsError(error(429, 'RATE_LIMITED'))).toBe(COMMON_MESSAGES.tooManyAttempts);
  });
});
