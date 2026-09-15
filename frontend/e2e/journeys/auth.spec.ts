import { expect, test } from '@playwright/test';

const BACKEND_URL = process.env.E2E_BACKEND_URL ?? 'http://localhost:8080';

test('register, confirm email, log in, and see a verified profile', async ({ page, request }) => {
  const email = `journey-${Date.now()}@x.com`;
  const password = 'password123';

  await page.goto('/register');
  await page.locator('#email').fill(email);
  await page.locator('#password').fill(password);
  await page.locator('#confirmPassword').fill(password);
  await page.getByRole('button', { name: 'Create account' }).click();

  await expect(page).toHaveURL(/\/check-email/);

  // No real inbox in this environment - the e2e-profiled backend records the
  // confirmation email instead of sending it (see TestSupportController, gated by
  // the "e2e" Spring profile, never active in the prod deployment config).
  const tokenResponse = await request.get(
    `${BACKEND_URL}/test-support/verification-token?email=${encodeURIComponent(email)}`,
  );
  expect(tokenResponse.ok()).toBe(true);
  const { token } = await tokenResponse.json();

  await page.goto(`/confirm-email?token=${token}`);
  await expect(page.getByRole('heading', { name: 'Email confirmed' })).toBeVisible();

  await page.goto('/login');
  await page.locator('#email').fill(email);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: 'Log in' }).click();

  await expect(page).toHaveURL('/');

  // The browser context's HttpOnly refresh cookie is set by the UI login above -
  // page.request shares that cookie jar, so this reuses the same session rather than
  // starting a new one.
  const refreshResponse = await page.request.post('/api/auth/refresh');
  expect(refreshResponse.ok()).toBe(true);
  const { accessToken } = await refreshResponse.json();

  const meResponse = await page.request.get('/api/auth/me', {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  expect(meResponse.ok()).toBe(true);
  const me = await meResponse.json();
  expect(me.email).toBe(email);
  expect(me.emailVerified).toBe(true);
});
