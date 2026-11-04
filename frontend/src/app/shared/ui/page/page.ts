import { Component, computed, input } from '@angular/core';

export type PageWidth = 'narrow' | 'default' | 'wide';

const WIDTHS: Record<PageWidth, string> = {
  narrow: 'max-w-[452px]', // a form: 420 px of content
  default: 'max-w-[824px]', // reading, a list, the day's report
  wide: 'max-w-[1184px]', // the game, photo and case side by side
};

/**
 * The frame of every page: one width per kind of content and the same margins
 * everywhere, so that screens no longer each pick their own.
 */
@Component({
  selector: 'app-page',
  host: { '[class]': 'classes()' },
  template: `<ng-content />`,
})
export class Page {
  readonly width = input<PageWidth>('default');

  protected readonly classes = computed(
    () => `mx-auto block w-full px-4 pt-5 pb-10 sm:px-8 sm:pt-12 sm:pb-16 ${WIDTHS[this.width()]}`,
  );
}
