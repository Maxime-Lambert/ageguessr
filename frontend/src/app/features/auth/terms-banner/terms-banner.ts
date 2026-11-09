import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { COMMON_MESSAGES } from '../../../shared/common-messages';
import { SiteLanguageService } from '../../i18n/site-language';
import { TERMS_VERSION, displayDate } from '../../legal/legal-dates';
import { Auth } from '../auth';
import { Button } from '../../../shared/ui/button/button';

/** Per tab: "Later" hides the banner until the tab is closed, then it asks again. */
const LATER_KEY = 'ageguessr_terms_later';

/**
 * Asks a signed-in player who accepted older Terms, or none, to accept the ones in force
 * (Terms, article 9). Not blocking: the player can carry on and answer later. Accepting
 * sends the version shown here; if the Terms changed since, the profile is read again.
 */
@Component({
  imports: [RouterLink, Button],
  selector: 'app-terms-banner',
  templateUrl: './terms-banner.html',
})
export class TermsBanner {
  private readonly auth = inject(Auth);

  protected readonly updated = displayDate(TERMS_VERSION, inject(SiteLanguageService).current);

  readonly later = signal(readLater());
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  readonly visible = computed(() => {
    const user = this.auth.currentUser();
    return user !== null && !user.termsAccepted && !this.later();
  });

  accept(): void {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.auth.acceptTerms(TERMS_VERSION).subscribe({
      next: () => this.busy.set(false),
      error: (error: HttpErrorResponse) => {
        this.busy.set(false);
        if (error.status === 409) {
          // Changed since this page was loaded: the profile says what is in force now.
          this.auth.me().subscribe({ error: () => undefined });
          return;
        }
        this.error.set(COMMON_MESSAGES.genericError);
      },
    });
  }

  postpone(): void {
    this.later.set(true);
    try {
      sessionStorage.setItem(LATER_KEY, TERMS_VERSION);
    } catch {
      // Storage blocked: hidden for this page only.
    }
  }
}

/** Postponed in this tab, for this version: a newer one asks again. */
function readLater(): boolean {
  try {
    return sessionStorage.getItem(LATER_KEY) === TERMS_VERSION;
  } catch {
    return false;
  }
}
