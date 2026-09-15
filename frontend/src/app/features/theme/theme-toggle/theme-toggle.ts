import { Component, inject } from '@angular/core';
import { LucideMoon, LucideSun } from '@lucide/angular';
import { Theme } from '../theme';

@Component({
  imports: [LucideMoon, LucideSun],
  selector: 'app-theme-toggle',
  templateUrl: './theme-toggle.html',
})
export class ThemeToggle {
  protected readonly themeService = inject(Theme);
}
