import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Auth } from '../../auth/auth';
import { ThemeToggle } from '../../theme/theme-toggle/theme-toggle';

@Component({
  imports: [RouterLink, ThemeToggle],
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
