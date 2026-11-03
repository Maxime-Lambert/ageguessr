/**
 * Messages that several screens show, translated once (Angular i18n, $localize). The
 * front end never shows the API's own message: it picks one of its texts from errorCode.
 */
export const COMMON_MESSAGES = {
  genericError: $localize`:@@common.genericError:Something went wrong. Please try again.`,
  tooManyAttempts: $localize`:@@common.tooManyAttempts:Too many attempts. Please wait a minute and try again.`,
};
