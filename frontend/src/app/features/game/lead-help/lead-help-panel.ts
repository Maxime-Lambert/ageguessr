import { Component, computed, input } from '@angular/core';
import { leadHelp } from '../lead-help-texts';
import { HintCategory } from '../models';

/**
 * What a lead category gives, as a card: the kind of fact, the rule, a worked example. The
 * panel only draws; who opens it, and where it sits, is up to its host (the lead card
 * before the pick, the ⓘ of a revealed hint after).
 */
@Component({
  selector: 'app-lead-help-panel',
  template: `
    <div
      class="card border-accent space-y-1.5 p-3.5 text-left text-sm font-normal tracking-normal normal-case shadow-lg"
    >
      <p class="font-semibold">{{ help().kind }}</p>
      <p>{{ help().rule }}</p>
      <p class="text-muted italic">{{ help().example }}</p>
    </div>
  `,
})
export class LeadHelpPanel {
  readonly category = input.required<HintCategory>();
  readonly gameYear = input.required<number>();

  protected readonly help = computed(() => leadHelp(this.category(), this.gameYear()));
}
