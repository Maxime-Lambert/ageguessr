export interface AuthUser {
  id: string;
  email: string;
  username: string;
  emailVerified: boolean;
}

export interface LoginResponse {
  accessToken: string;
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

export interface ApiErrorResponse {
  errorCode: string;
  message: string;
}
