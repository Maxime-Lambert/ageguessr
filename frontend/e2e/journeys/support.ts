import { APIRequestContext, Page, expect } from '@playwright/test';

export const BACKEND_URL = process.env.E2E_BACKEND_URL ?? 'http://localhost:8080';

// Angular serves one language per dev server: the English site on 4200, the French one
// on 4201 (see playwright.config.ts). In production, Caddy serves both on one origin.
const ENGLISH_SITE = process.env.E2E_BASE_URL ?? 'http://localhost:4200';
const FRENCH_SITE = process.env.E2E_FR_BASE_URL ?? 'http://localhost:4201';

/**
 * No real inbox in this environment - the e2e-profiled backend records emails instead
 * of sending them (TestSupportController, gated by the "e2e" Spring profile, never
 * active in the prod deployment config). Returns the subject and the link of the last
 * email sent to an address.
 */
export async function lastEmail(
  request: APIRequestContext,
  email: string,
): Promise<{ subject: string; link: URL }> {
  const response = await request.get(
    `${BACKEND_URL}/test-support/last-email?email=${encodeURIComponent(email)}`,
  );
  expect(response.ok()).toBe(true);
  const { subject, link } = await response.json();
  return { subject, link: new URL(link) };
}

/**
 * Opens the link of the last email, as the player would: its path, language prefix
 * included, is kept as is, and only the origin is swapped for the dev server of that
 * language. Returns the email, for the caller to check its subject or its link.
 */
export async function openLastEmailLink(
  page: Page,
  request: APIRequestContext,
  email: string,
): Promise<{ subject: string; link: URL }> {
  const last = await lastEmail(request, email);
  const site = last.link.pathname.startsWith('/fr/') ? FRENCH_SITE : ENGLISH_SITE;
  await page.goto(`${site}${last.link.pathname}${last.link.search}`);
  return last;
}

export function uniqueAccount(prefix: string) {
  const id = `${Date.now()}${Math.floor(Math.random() * 1000)}`;
  return {
    email: `${prefix}-${id}@x.com`,
    username: `${prefix.slice(0, 4)}${id}`.slice(0, 20),
    password: 'tortoise-violin-42',
  };
}

/** Full sign-up through the UI, confirmed through the emailed link. */
export async function signUp(
  page: Page,
  request: APIRequestContext,
  account: { email: string; username: string; password: string },
): Promise<void> {
  await page.goto('/en/register');
  await page.locator('#email').fill(account.email);
  await page.locator('#username').fill(account.username);
  await page.locator('#password').fill(account.password);
  await page.locator('#confirmPassword').fill(account.password);
  await page.locator('#acceptTerms').check();
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page).toHaveURL(/\/en\/check-email/);

  const { link } = await openLastEmailLink(page, request, account.email);
  expect(link.pathname).toBe('/en/confirm-email');
  await expect(page.getByRole('heading', { name: 'Email confirmed' })).toBeVisible();
}

export async function logIn(page: Page, email: string, password: string): Promise<void> {
  await page.goto('/en/login');
  await page.locator('#email').fill(email);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: 'Log in' }).click();
}
