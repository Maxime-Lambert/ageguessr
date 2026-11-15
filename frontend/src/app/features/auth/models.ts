import { ApiLanguage } from '../../shared/i18n/language';

export type UserRole = 'USER' | 'ADMIN';

export interface AuthUser {
  id: string;
  email: string;
  username: string;
  emailVerified: boolean;
  /** Only decides what the interface shows: the API checks the role on every admin request. */
  role: UserRole;
  /** Of the emails and of the site once signed in; null until chosen (accounts before V14). */
  language: ApiLanguage | null;
  /** False when the account accepted an older version of the Terms, or none: it is asked again. */
  termsAccepted: boolean;
  /** Signing in asks for a code of an authenticator app. */
  twoFactorEnabled: boolean;
}

/** Signed in (accessToken), or the password is right and a two-factor code is due (challenge). */
export interface LoginResponse {
  accessToken?: string;
  twoFactorRequired?: boolean;
  challenge?: string;
}

/** The end of the first step: signed in, or a challenge to send with the code. */
export type LoginResult = { user: AuthUser } | { challenge: string };

/** Shown once when turning two-factor on: for the QR code, and to type by hand. */
export interface TwoFactorSetup {
  secret: string;
  otpauthUri: string;
}

export interface RefreshResponse {
  accessToken: string;
}

export interface RegisterResponse {
  userId: string;
  email: string;
  username: string;
}

/** Same rule as the backend's @Username constraint, which stays the source of truth. */
export const USERNAME_PATTERN = /^[A-Za-z0-9_-]{3,20}$/;

export const USERNAME_RULE_MESSAGE = $localize`:@@common.usernameRule:3 to 20 characters: letters, digits, _ or -.`;
export const USERNAME_TAKEN_MESSAGE = $localize`:@@account.usernameTaken:This username is already taken.`;
