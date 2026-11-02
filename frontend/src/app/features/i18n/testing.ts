import { BrowserPage } from './site-language';

/** A page that records where it was sent and the cookies set, without leaving the spec. */
export function fakePage(): BrowserPage & { cookies: string[]; loaded: string[] } {
  const cookies: string[] = [];
  const loaded: string[] = [];
  return {
    cookies,
    loaded,
    secure: () => true,
    setCookie: (cookie) => cookies.push(cookie),
    load: (url) => loaded.push(url),
  };
}
