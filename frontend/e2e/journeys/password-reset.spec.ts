import { expect, test } from '@playwright/test';
import { lastEmailToken, logIn, signUp, uniqueAccount } from './support';

test('forgot password, reset through the emailed link, log in with the new password', async ({
  page,
  request,
}) => {
  const account = uniqueAccount('reset');
  await signUp(page, request, account);

  await page.goto('/login');
  await page.getByRole('link', { name: 'Forgot password?' }).click();
  await page.locator('#email').fill(account.email);
  await page.getByRole('button', { name: 'Send reset link' }).click();
  await expect(page.getByRole('heading', { name: 'Check your email' })).toBeVisible();

  await page.goto(`/reset-password?token=${await lastEmailToken(request, account.email)}`);
  await page.locator('#newPassword').fill('brand-new-password');
  await page.locator('#confirmPassword').fill('brand-new-password');
  await page.getByRole('button', { name: 'Set new password' }).click();
  await expect(page.getByRole('heading', { name: 'Password updated' })).toBeVisible();

  await logIn(page, account.email, account.password);
  await expect(page.getByText('Invalid email or password.')).toBeVisible();

  await logIn(page, account.email, 'brand-new-password');
  await expect(page).toHaveURL('/');
});

test('the "secure your account" link of the change notification takes the account back', async ({
  page,
  request,
}) => {
  const account = uniqueAccount('secure');
  await signUp(page, request, account);
  await logIn(page, account.email, account.password);
  await expect(page).toHaveURL('/');

  // Someone with the password changes it from the account page...
  await page.goto('/account');
  await page.locator('#currentPassword').fill(account.password);
  await page.locator('#newPassword').fill('intruder-password');
  await page.locator('#confirmNewPassword').fill('intruder-password');
  await page.getByRole('button', { name: 'Change password' }).click();
  await expect(page.getByText('Password changed.')).toBeVisible();

  // ...and the owner uses the link from the notification email.
  await page.goto(`/reset-password?token=${await lastEmailToken(request, account.email)}`);
  await page.locator('#newPassword').fill('owner-password');
  await page.locator('#confirmPassword').fill('owner-password');
  await page.getByRole('button', { name: 'Set new password' }).click();
  await expect(page.getByRole('heading', { name: 'Password updated' })).toBeVisible();

  await logIn(page, account.email, 'owner-password');
  await expect(page).toHaveURL('/');
});
