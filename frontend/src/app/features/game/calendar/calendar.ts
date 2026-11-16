import { Component, LOCALE_ID, computed, inject, signal } from '@angular/core';
import { formatDate } from '@angular/common';
import { RouterLink } from '@angular/router';
import { LucideCheck, LucideChevronLeft, LucideChevronRight } from '@lucide/angular';
import { Button } from '../../../shared/ui/button/button';
import { Page } from '../../../shared/ui/page/page';
import { GameApi } from '../game-api';
import { GAME_TEXT, calendarDate } from '../game-vocabulary';
import { CalendarMonth, DayState } from '../models';

/** One square of the grid: a day of the month, or an empty square before the 1st. */
export interface CalendarCell {
  date: string;
  dayOfMonth: number;
  state: DayState;
  today: boolean;
  /** Where the square leads: today's game at /, a past day at /day/…; null when unavailable. */
  link: string | null;
  label: string;
  /** Tailwind classes of the square, by state, today ringed. */
  classes: string;
}

/**
 * The past daily games, month by month, at /calendar: one square per day, coloured by its
 * state for the player (GetCalendar), from which any past day not finished is played.
 * Weeks start on Monday. The days are the draw's (UTC), given by the API, so that the
 * visitor's time zone never moves them.
 */
@Component({
  imports: [RouterLink, Button, Page, LucideCheck, LucideChevronLeft, LucideChevronRight],
  selector: 'app-calendar',
  templateUrl: './calendar.html',
})
export class Calendar {
  private readonly api = inject(GameApi);
  private readonly locale = inject(LOCALE_ID);

  protected readonly text = { ...GAME_TEXT, ...CALENDAR_TEXT };
  protected readonly legend = (['AVAILABLE', 'STARTED', 'FINISHED', 'UNAVAILABLE'] as const).map(
    (state) => ({
      state,
      label: STATE_LABELS[state],
      classes: `${DAY_BASE} size-5 ${DAY_CLASSES[state]}`,
    }),
  );

  readonly month = signal<CalendarMonth | null>(null);
  readonly status = signal<'loading' | 'ready' | 'error'>('loading');

  /** "November 2026" / "novembre 2026". */
  readonly title = computed(() => {
    const month = this.month();
    return month ? formatDate(`${month.month}-01T00:00:00Z`, 'MMMM y', this.locale, 'UTC') : '';
  });
  readonly weekdays = computed(() =>
    // 2024-01-01 was a Monday: seven days from there name the columns.
    Array.from({ length: 7 }, (_, index) =>
      formatDate(`2024-01-0${index + 1}T00:00:00Z`, 'EEE', this.locale, 'UTC'),
    ),
  );
  /** Empty squares before the 1st, so that it falls under its weekday. */
  readonly offset = computed(() => {
    const month = this.month();
    if (!month) {
      return [];
    }
    const sundayFirst = new Date(`${month.month}-01T00:00:00Z`).getUTCDay();
    return Array.from({ length: (sundayFirst + 6) % 7 });
  });
  readonly cells = computed<CalendarCell[]>(() => {
    const month = this.month();
    return (month?.days ?? []).map((day) => {
      const today = day.date === month!.today;
      return {
        date: day.date,
        dayOfMonth: Number(day.date.slice(8, 10)),
        state: day.state,
        today,
        link: day.state === 'UNAVAILABLE' ? null : today ? '/' : `/day/${day.date}`,
        label: `${calendarDate(day.date, this.locale)}, ${STATE_LABELS[day.state]}${
          today ? `, ${CALENDAR_TEXT.today}` : ''
        }`,
        classes: `${DAY_BASE} aspect-square text-sm ${DAY_CLASSES[day.state]}${today ? ` ${TODAY}` : ''}`,
      };
    });
  });
  readonly hasPrevious = computed(() => {
    const month = this.month();
    return month !== null && month.month > month.firstMonth;
  });
  readonly hasNext = computed(() => {
    const month = this.month();
    return month !== null && month.month < month.today.slice(0, 7);
  });

  constructor() {
    this.load();
  }

  load(month?: string): void {
    this.status.set('loading');
    this.api.calendar(month).subscribe({
      next: (loaded) => {
        this.month.set(loaded);
        this.status.set('ready');
      },
      error: () => this.status.set('error'),
    });
  }

  previous(): void {
    this.move(-1);
  }

  next(): void {
    this.move(1);
  }

  /** "2026-11" plus or minus a month, by the calendar's own arithmetic. */
  private move(months: number): void {
    const current = this.month();
    if (!current) {
      return;
    }
    const [year, month] = current.month.split('-').map(Number);
    const moved = new Date(Date.UTC(year, month - 1 + months, 1));
    this.load(`${moved.getUTCFullYear()}-${String(moved.getUTCMonth() + 1).padStart(2, '0')}`);
  }
}

const CALENDAR_TEXT = {
  intro: $localize`:@@calendar.intro:Missed a day? Every past daily game can still be played.`,
  previousMonth: $localize`:@@calendar.previousMonth:Previous month`,
  nextMonth: $localize`:@@calendar.nextMonth:Next month`,
  available: $localize`:@@calendar.available:To play`,
  started: $localize`:@@calendar.started:Started`,
  finished: $localize`:@@calendar.finished:Finished`,
  unavailable: $localize`:@@calendar.unavailable:No game`,
  today: $localize`:@@calendar.today:today`,
  legend: $localize`:@@calendar.legend:Legend`,
};

const STATE_LABELS: Record<DayState, string> = {
  AVAILABLE: CALENDAR_TEXT.available,
  STARTED: CALENDAR_TEXT.started,
  FINISHED: CALENDAR_TEXT.finished,
  UNAVAILABLE: CALENDAR_TEXT.unavailable,
};

const DAY_BASE = 'relative flex items-center justify-center rounded-md font-semibold';
/**
 * The colour is never the only cue (WCAG 1.4.1): a started day has a dashed border and a
 * dot, a finished one a tick, an unavailable one no box at all.
 */
const DAY_CLASSES: Record<DayState, string> = {
  AVAILABLE: 'bg-surface-2 text-foreground border border-border hover:border-accent',
  STARTED: 'bg-surface text-foreground border-2 border-dashed border-score hover:border-accent',
  FINISHED: 'bg-score text-background hover:opacity-90',
  UNAVAILABLE: 'text-muted opacity-60',
};
const TODAY = 'ring-2 ring-accent ring-offset-2 ring-offset-surface';
