import { Routes } from '@angular/router';
import { authGuard } from './features/auth/auth-guard';
import { guestGuard } from './features/auth/guest-guard';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./features/home/home').then((m) => m.Home),
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register/register').then((m) => m.Register),
  },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: 'check-email',
    loadComponent: () =>
      import('./features/auth/check-email/check-email').then((m) => m.CheckEmail),
  },
  {
    path: 'confirm-email',
    loadComponent: () =>
      import('./features/auth/confirm-email/confirm-email').then((m) => m.ConfirmEmail),
  },
  {
    path: 'forgot-password',
    loadComponent: () =>
      import('./features/auth/forgot-password/forgot-password').then((m) => m.ForgotPassword),
  },
  {
    // Not guest-only: the "secure your account" link must work even on a device
    // where the (possibly compromised) account is still logged in.
    path: 'reset-password',
    loadComponent: () =>
      import('./features/auth/reset-password/reset-password').then((m) => m.ResetPassword),
  },
  {
    path: 'account',
    canActivate: [authGuard],
    loadComponent: () => import('./features/auth/account/account').then((m) => m.Account),
  },
  {
    path: 'confirm-account-deletion',
    loadComponent: () =>
      import('./features/auth/confirm-account-deletion/confirm-account-deletion').then(
        (m) => m.ConfirmAccountDeletion,
      ),
  },
  {
    path: 'legal-notice',
    title: 'Legal notice · Ageguessr',
    loadComponent: () =>
      import('./features/legal/legal-notice/legal-notice').then((m) => m.LegalNotice),
  },
  {
    path: 'terms',
    title: 'Terms of Use · Ageguessr',
    loadComponent: () => import('./features/legal/terms/terms').then((m) => m.Terms),
  },
  {
    path: 'privacy',
    title: 'Privacy Policy · Ageguessr',
    loadComponent: () => import('./features/legal/privacy/privacy').then((m) => m.Privacy),
  },
  { path: '**', redirectTo: '' },
];
