import { Dialog, DialogRef } from '@angular/cdk/dialog';
import { createGlobalPositionStrategy } from '@angular/cdk/overlay';
import { NgTemplateOutlet } from '@angular/common';
import { Component, Injector, TemplateRef, inject, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationStart, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter } from 'rxjs';
import {
  LucideChevronDown,
  LucideFolderOpen,
  LucideHistory,
  LucideImages,
  LucideLightbulb,
  LucideLogOut,
  LucideMenu,
  LucideSearch,
  LucideGlobe,
  LucideUser,
  LucideX,
} from '@lucide/angular';
import { Button } from '../../../shared/ui/button/button';
import { Dropdown } from '../../../shared/ui/dropdown/dropdown';
import { Auth } from '../../auth/auth';
import { GAME_TEXT } from '../../game/game-vocabulary';
import { SiteLanguageService } from '../../i18n/site-language';
import { Preferences } from '../preferences/preferences';

@Component({
  imports: [
    RouterLink,
    RouterLinkActive,
    NgTemplateOutlet,
    Button,
    Dropdown,
    Preferences,
    LucideChevronDown,
    LucideFolderOpen,
    LucideHistory,
    LucideImages,
    LucideLightbulb,
    LucideLogOut,
    LucideMenu,
    LucideSearch,
    LucideGlobe,
    LucideUser,
    LucideX,
  ],
  selector: 'app-header',
  templateUrl: './header.html',
})
export class Header {
  private readonly router = inject(Router);
  private readonly dialog = inject(Dialog);
  private readonly injector = inject(Injector);
  private readonly sheetTemplate = viewChild.required<TemplateRef<unknown>>('sheet');
  private sheetRef: DialogRef | null = null;
  protected readonly auth = inject(Auth);
  /** Shown on the guest's preferences button, so it reads as the language switch it is. */
  protected readonly languageCode = inject(SiteLanguageService).current.toUpperCase();

  protected readonly text = {
    mainNav: $localize`:@@header.mainNav:Main`,
    login: $localize`:@@header.login:Log in`,
    signup: $localize`:@@header.signup:Sign up`,
    logout: $localize`:@@header.logout:Log out`,
    myAccount: $localize`:@@header.myAccount:My account`,
    profile: $localize`:@@header.profile:My profile`,
    preferences: $localize`:@@header.preferences:Preferences`,
    menu: $localize`:@@header.menu:Menu`,
    closeMenu: $localize`:@@header.closeMenu:Close menu`,
  };
  /** The game modes, named in the game's own vocabulary (game-vocabulary.ts). */
  protected readonly gameLinks = [
    { path: '/', label: GAME_TEXT.dailyGame },
    { path: '/free', label: GAME_TEXT.freeMode },
  ];

  constructor() {
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationStart),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.closeSheet());
  }

  protected initial(username: string): string {
    return username.charAt(0).toUpperCase();
  }

  openSheet(): void {
    this.sheetRef = this.dialog.open(this.sheetTemplate(), {
      ariaLabel: this.text.menu,
      width: 'min(86vw, 360px)',
      height: '100%',
      positionStrategy: createGlobalPositionStrategy(this.injector).top('0').right('0'),
      panelClass: 'app-sheet',
    });
    this.sheetRef.closed.subscribe(() => (this.sheetRef = null));
  }

  closeSheet(): void {
    this.sheetRef?.close();
  }

  logout(): void {
    this.auth.logout().subscribe({
      complete: () => this.router.navigate(['/']),
      error: () => this.router.navigate(['/']),
    });
  }
}
