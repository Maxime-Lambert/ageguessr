import { Component, input, output } from '@angular/core';

/**
 * The photo of a case as a mounted print: a paper mat around it, slightly tilted on a wide
 * screen, and a caption below (« Case 1 », then the name and the credit once revealed).
 * The print takes the photo's own shape: the photo is never cropped, which could hide
 * what the player needs to judge. On a phone it keeps under half the screen, so the
 * question and the age field stay in view with it.
 */
@Component({
  selector: 'app-case-photo',
  host: { class: 'block' },
  template: `
    <figure
      class="bg-paper shadow-print mx-auto w-fit max-w-full px-2.5 pt-2.5 md:-rotate-[0.6deg] md:px-3.5 md:pt-3.5"
    >
      <img
        class="archive-photo block max-h-[45vh] w-auto max-w-full md:max-h-[min(70vh,640px)]"
        [src]="src()"
        [alt]="alt()"
        (error)="failed.emit()"
      />
      <figcaption
        class="text-paper-foreground flex items-start justify-between gap-3 px-0.5 pt-2.5 pb-3"
      >
        <span class="font-display text-[15px] italic">{{ caption() }}</span>
        <ng-content />
      </figcaption>
    </figure>
  `,
})
export class CasePhoto {
  readonly src = input.required<string>();
  readonly alt = input.required<string>();
  readonly caption = input.required<string>();

  readonly failed = output<void>();
}
