import { Routes } from '@angular/router';
import { authGuard } from './features/auth/auth-guard';
import { guestGuard } from './features/auth/guest-guard';
import { adminGuard } from './features/auth/admin-guard';
import { SEO_TEXT } from './features/seo/seo-texts';
import type { SeoData } from './features/seo/seo';

/** What search engines read of a page (features/seo). */
const PRIVATE: { data: SeoData } = { data: { noindex: true } };
const describedAs = (description: string): { data: SeoData } => ({ data: { description } });

export const routes: Routes = [
  {
    // The game is the home page, as for a daily puzzle: one arrives and plays.
    path: '',
    ...describedAs(SEO_TEXT.description),
    pathMatch: 'full',
    loadComponent: () => import('./features/game/game/game').then((m) => m.Game),
  },
  {
    path: 'free',
    ...describedAs(SEO_TEXT.free),
    title: $localize`:@@title.free:Free play · AgeGuessr`,
    loadComponent: () => import('./features/game/free-game/free-game').then((m) => m.FreeGame),
  },
  {
    path: 'calendar',
    ...describedAs(SEO_TEXT.calendar),
    title: $localize`:@@title.calendar:Calendar · AgeGuessr`,
    loadComponent: () => import('./features/game/calendar/calendar').then((m) => m.Calendar),
  },
  {
    // A past day's game, from the calendar: the same screen as today's. Not indexed, one
    // URL per day.
    path: 'day/:date',
    ...PRIVATE,
    title: $localize`:@@title.pastDay:Past game · AgeGuessr`,
    loadComponent: () => import('./features/game/game/game').then((m) => m.Game),
  },
  {
    path: 'how-to-play',
    ...describedAs(SEO_TEXT.howToPlay),
    title: $localize`:@@title.howToPlay:How to play · AgeGuessr`,
    loadComponent: () =>
      import('./features/game/how-to-play/how-to-play-page').then((m) => m.HowToPlayPage),
  },
  {
    path: 'challenge/:code',
    ...PRIVATE,
    title: $localize`:@@title.challenge:Challenge · AgeGuessr`,
    loadComponent: () =>
      import('./features/game/challenge/challenge-page').then((m) => m.ChallengePage),
  },
  {
    path: 'register',
    ...PRIVATE,
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register/register').then((m) => m.Register),
  },
  {
    path: 'login',
    ...PRIVATE,
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: 'check-email',
    ...PRIVATE,
    loadComponent: () =>
      import('./features/auth/check-email/check-email').then((m) => m.CheckEmail),
  },
  {
    path: 'confirm-email',
    ...PRIVATE,
    loadComponent: () =>
      import('./features/auth/confirm-email/confirm-email').then((m) => m.ConfirmEmail),
  },
  {
    path: 'forgot-password',
    ...PRIVATE,
    loadComponent: () =>
      import('./features/auth/forgot-password/forgot-password').then((m) => m.ForgotPassword),
  },
  {
    // Not guest-only: the "secure your account" link must work even on a device
    // where the (possibly compromised) account is still logged in.
    path: 'reset-password',
    ...PRIVATE,
    loadComponent: () =>
      import('./features/auth/reset-password/reset-password').then((m) => m.ResetPassword),
  },
  {
    path: 'account',
    ...PRIVATE,
    canActivate: [authGuard],
    loadComponent: () => import('./features/auth/account/account').then((m) => m.Account),
  },
  {
    path: 'profile',
    ...PRIVATE,
    canActivate: [authGuard],
    title: $localize`:@@title.profile:My profile · AgeGuessr`,
    loadComponent: () => import('./features/game/profile/profile').then((m) => m.Profile),
  },
  {
    path: 'friends',
    ...PRIVATE,
    canActivate: [authGuard],
    title: $localize`:@@title.friends:Friends · AgeGuessr`,
    loadComponent: () =>
      import('./features/friends/friends-page/friends-page').then((m) => m.FriendsPage),
  },
  // Invite links were removed (2026-11-11): one already shared opens the friends page,
  // where adding by username is the way now.
  { path: 'friends/invite/:code', redirectTo: 'friends' },
  {
    path: 'confirm-account-deletion',
    ...PRIVATE,
    loadComponent: () =>
      import('./features/auth/confirm-account-deletion/confirm-account-deletion').then(
        (m) => m.ConfirmAccountDeletion,
      ),
  },
  {
    // adminGuard is only the interface's gate: the API checks the role on every call.
    path: 'admin/curation',
    ...PRIVATE,
    title: 'Photo curation · AgeGuessr',
    canActivate: [adminGuard],
    loadComponent: () => import('./features/curation/curation/curation').then((m) => m.Curation),
  },
  {
    path: 'admin/hints',
    ...PRIVATE,
    title: 'Hint curation · AgeGuessr',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./features/hint-curation/hint-curation/hint-curation').then((m) => m.HintCuration),
  },
  {
    path: 'legal-notice',
    ...describedAs(SEO_TEXT.legalNotice),
    title: $localize`:@@title.legalNotice:Legal notice · AgeGuessr`,
    loadComponent: () =>
      import('./features/legal/legal-notice/legal-notice').then((m) => m.LegalNotice),
  },
  {
    path: 'terms',
    ...describedAs(SEO_TEXT.terms),
    title: $localize`:@@title.terms:Terms of Use · AgeGuessr`,
    loadComponent: () => import('./features/legal/terms/terms').then((m) => m.Terms),
  },
  {
    path: 'privacy',
    ...describedAs(SEO_TEXT.privacy),
    title: $localize`:@@title.privacy:Privacy Policy · AgeGuessr`,
    loadComponent: () => import('./features/legal/privacy/privacy').then((m) => m.Privacy),
  },
  { path: '**', redirectTo: '' },
];
