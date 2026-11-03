import { Component, effect, inject, untracked } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Auth } from './features/auth/auth';
import { TermsBanner } from './features/auth/terms-banner/terms-banner';
import { SiteLanguageService } from './features/i18n/site-language';
import { Footer } from './features/layout/footer/footer';
import { Header } from './features/layout/header/header';

@Component({
  imports: [RouterOutlet, Header, TermsBanner, Footer],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  private readonly auth = inject(Auth);
  private readonly language = inject(SiteLanguageService);
  /** The account whose language was already aligned, so a profile update does not redo it. */
  private alignedUserId: string | null = null;

  constructor() {
    // Silently try to restore a session from the HttpOnly refresh cookie on load -
    // failure just means "guest", never surfaced as an error.
    this.auth.tryRestoreSession().subscribe();

    // Once per signed-in account, at session restore or login: the account's language wins.
    effect(() => {
      const user = this.auth.currentUser();
      if (!user || user.id === this.alignedUserId) {
        return;
      }
      this.alignedUserId = user.id;
      untracked(() => this.language.alignWith(user)?.subscribe({ error: () => undefined }));
    });
  }
}
