import { Page } from '@playwright/test';

export const USER = {
  id: '1',
  email: 'me@x.com',
  username: 'Player',
  emailVerified: true,
  role: 'USER' as const,
};

/** The app tries to restore a session on load: answer it deterministically. */
export async function asGuest(page: Page): Promise<void> {
  await page.route('**/api/auth/refresh', (route) =>
    route.fulfill({ status: 401, contentType: 'application/json', body: '{}' }),
  );
}

export async function asLoggedIn(page: Page, user = USER): Promise<void> {
  await page.route('**/api/auth/refresh', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ accessToken: 'access-token' }),
    }),
  );
  await page.route('**/api/auth/me', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(user) }),
  );
}
