import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ThemeToggle } from './features/theme/theme-toggle/theme-toggle';

@Component({
  imports: [RouterOutlet, ThemeToggle],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {}
