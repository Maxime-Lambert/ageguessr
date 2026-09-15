export interface AuthUser {
  id: string;
  email: string;
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
}

export interface ApiErrorResponse {
  errorCode: string;
  message: string;
}
