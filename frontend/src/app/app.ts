import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Auth } from './features/auth/auth';
import { ThemeToggle } from './features/theme/theme-toggle/theme-toggle';

@Component({
  imports: [RouterOutlet, ThemeToggle],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  constructor() {
    // Silently try to restore a session from the HttpOnly refresh cookie on load -
    // failure just means "guest", never surfaced as an error.
    inject(Auth).tryRestoreSession().subscribe();
  }
}
