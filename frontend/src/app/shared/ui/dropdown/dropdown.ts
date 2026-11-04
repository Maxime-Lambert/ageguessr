import { Component, ElementRef, inject, input, signal, viewChild } from '@angular/core';

let nextId = 0;

/**
 * A button that shows a panel below it: the account menu, the preferences. A disclosure
 * (aria-expanded) rather than an ARIA menu: the panel mixes links and radio groups, which
 * a role="menu" may not hold, and a menu would take over the arrow keys the radio groups
 * need. The panel follows the button in the page, so Tab goes straight into it.
 * Escape, a click outside and following a link close it.
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

  protected clickedAnywhere(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }

  protected clickedInside(event: MouseEvent): void {
    const link = (event.target as Element).closest('a');
    if (link && this.host.nativeElement.contains(link)) this.open.set(false);
  }

  protected escape(): void {
    if (!this.open()) return;
    this.open.set(false);
    this.trigger().nativeElement.focus();
  }
}
