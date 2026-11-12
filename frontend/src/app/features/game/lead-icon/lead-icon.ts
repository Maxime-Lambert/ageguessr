import { Component, input } from '@angular/core';
import {
  LucideBookOpen,
  LucideClapperboard,
  LucideFlaskConical,
  LucideGlobe,
  LucideMusic,
  LucideSparkles,
  LucideTrophy,
} from '@lucide/angular';
import { HintCategory } from '../models';

/** The icon of a lead category. Decorative: the category's name always goes with it. */
@Component({
  selector: 'app-lead-icon',
  imports: [
    LucideBookOpen,
    LucideClapperboard,
    LucideFlaskConical,
    LucideGlobe,
    LucideMusic,
    LucideSparkles,
    LucideTrophy,
  ],
  host: { class: 'inline-flex', 'aria-hidden': 'true' },
  template: `
    @switch (category()) {
      @case ('MUSIC') {
        <svg lucideMusic [size]="size()"></svg>
      }
      @case ('FILM') {
        <svg lucideClapperboard [size]="size()"></svg>
      }
      @case ('SPORT') {
        <svg lucideTrophy [size]="size()"></svg>
      }
      @case ('SCIENCE') {
        <svg lucideFlaskConical [size]="size()"></svg>
      }
      @case ('WORLD') {
        <svg lucideGlobe [size]="size()"></svg>
      }
      @case ('CELEBRITIES') {
        <svg lucideSparkles [size]="size()"></svg>
      }
      @case ('LITERATURE') {
        <svg lucideBookOpen [size]="size()"></svg>
      }
    }
  `,
})
export class LeadIcon {
  readonly category = input.required<HintCategory>();
  readonly size = input(22);
}
