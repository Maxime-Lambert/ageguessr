import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Auth } from '../../auth/auth';
import { LanguageSwitch } from '../../i18n/language-switch/language-switch';
import { ThemeToggle } from '../../theme/theme-toggle/theme-toggle';

@Component({
  imports: [RouterLink, LanguageSwitch, ThemeToggle],
  selector: 'app-header',
  templateUrl: './header.html',
})
export class Header {
  private readonly router = inject(Router);
  protected readonly auth = inject(Auth);

  logout(): void {
    this.auth.logout().subscribe({
      complete: () => this.router.navigate(['/']),
      error: () => this.router.navigate(['/']),
    });
  }
}
