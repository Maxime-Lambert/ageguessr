/** Body of every API error (GlobalExceptionHandler in ageguessr-api), whatever the feature. */
export interface ApiErrorResponse {
  errorCode: string;
  message: string;
}
