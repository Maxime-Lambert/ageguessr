import { Component, ElementRef, inject, input, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationStart, Router } from '@angular/router';
import { filter } from 'rxjs';

let nextId = 0;

/**
 * A button that shows a panel below it: the account menu, the preferences. A disclosure
 * (aria-expanded) rather than an ARIA menu: the panel mixes links and radio groups, which
 * a role="menu" may not hold, and a menu would take over the arrow keys the radio groups
 * need. The panel follows the button in the page, so Tab goes straight into it.
 * Escape, a click outside, a router navigation and following a plain link close it.
 */
@Component({
  selector: 'app-dropdown',
  host: {
    class: 'relative inline-block',
    '(document:click)': 'clickedAnywhere($event)',
    // On the host rather than the panel: Enter on a link fires a click too, so following a
    // link closes the panel from the keyboard as well.
    '(click)': 'clickedInside($event)',
    '(keydown.escape)': 'escape()',
  },
  template: `
    <button
      #trigger
      type="button"
      [class]="triggerClass()"
      [attr.aria-label]="label()"
      [attr.aria-expanded]="open()"
      [attr.aria-controls]="panelId"
      (click)="open.set(!open())"
    >
      <ng-content select="[appDropdownTrigger]" />
    </button>
    <div
      class="bg-surface border-border shadow-overlay absolute top-full right-0 z-30 mt-2 w-[300px] rounded-lg border p-1.5"
      [id]="panelId"
      [hidden]="!open()"
    >
      <ng-content />
    </div>
  `,
})
export class Dropdown {
  /** The accessible name of the button, when its content alone does not say it. */
  readonly label = input<string | null>(null);
  readonly triggerClass = input('');

  protected readonly open = signal(false);
  protected readonly panelId = `dropdown-${nextId++}`;
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly trigger = viewChild.required<ElementRef<HTMLButtonElement>>('trigger');

  constructor() {
    // A routerLink cancels the browser's own navigation (the router does it), so its click
    // looks like a cancelled one: the navigation itself closes the panel.
    inject(Router, { optional: true })
      ?.events.pipe(
        filter((event) => event instanceof NavigationStart),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.open.set(false));
  }

  protected clickedAnywhere(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }

  protected clickedInside(event: MouseEvent): void {
    // A plain link closes the panel. One whose click was cancelled is not followed: a
    // routerLink (closed by the navigation, see the constructor), or the language switch,
    // which saves the account first and may show an error here, so the panel stays open.
    const link = (event.target as Element).closest('a');
    if (link && !event.defaultPrevented && this.host.nativeElement.contains(link)) {
      this.open.set(false);
    }
  }

  protected escape(): void {
    if (!this.open()) return;
    this.open.set(false);
    this.trigger().nativeElement.focus();
  }
}
