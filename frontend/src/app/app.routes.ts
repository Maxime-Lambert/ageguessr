import { Routes } from '@angular/router';
import { authGuard } from './features/auth/auth-guard';
import { guestGuard } from './features/auth/guest-guard';
import { adminGuard } from './features/auth/admin-guard';

export const routes: Routes = [
  {
    // The game is the home page, as for a daily puzzle: one arrives and plays.
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./features/game/game/game').then((m) => m.Game),
  },
  {
    path: 'free',
    title: $localize`:@@title.free:Free investigation · AgeGuessr`,
    loadComponent: () => import('./features/game/free-game/free-game').then((m) => m.FreeGame),
  },
  {
    path: 'challenge/:code',
    title: $localize`:@@title.challenge:Challenge · AgeGuessr`,
    loadComponent: () =>
      import('./features/game/challenge/challenge-page').then((m) => m.ChallengePage),
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
    path: 'profile',
    canActivate: [authGuard],
    title: $localize`:@@title.profile:My profile · AgeGuessr`,
    loadComponent: () => import('./features/game/profile/profile').then((m) => m.Profile),
  },
  {
    path: 'confirm-account-deletion',
    loadComponent: () =>
      import('./features/auth/confirm-account-deletion/confirm-account-deletion').then(
        (m) => m.ConfirmAccountDeletion,
      ),
  },
  {
    // adminGuard is only the interface's gate: the API checks the role on every call.
    path: 'admin/curation',
    title: 'Photo curation · AgeGuessr',
    canActivate: [adminGuard],
    loadComponent: () => import('./features/curation/curation/curation').then((m) => m.Curation),
  },
  {
    path: 'admin/hints',
    title: 'Hint curation · AgeGuessr',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./features/hint-curation/hint-curation/hint-curation').then((m) => m.HintCuration),
  },
  {
    path: 'legal-notice',
    title: $localize`:@@title.legalNotice:Legal notice · AgeGuessr`,
    loadComponent: () =>
      import('./features/legal/legal-notice/legal-notice').then((m) => m.LegalNotice),
  },
  {
    path: 'terms',
    title: $localize`:@@title.terms:Terms of Use · AgeGuessr`,
    loadComponent: () => import('./features/legal/terms/terms').then((m) => m.Terms),
  },
  {
    path: 'privacy',
    title: $localize`:@@title.privacy:Privacy Policy · AgeGuessr`,
    loadComponent: () => import('./features/legal/privacy/privacy').then((m) => m.Privacy),
  },
  { path: '**', redirectTo: '' },
];
