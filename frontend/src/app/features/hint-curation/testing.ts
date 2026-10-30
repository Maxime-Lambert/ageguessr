import { HintCoverage, PendingHint } from './models';

/** A hint translated for the French audience. */
export function pendingHint(id: string, overrides: Partial<PendingHint> = {}): PendingHint {
  return {
    hintId: id,
    category: 'SCIENCE',
    year: 1976,
    audience: 'FR',
    textEn: null,
    textFr: `Découverte ${id}.`,
    sourceUrl: `https://en.wikipedia.org/wiki/${id}`,
    sourceKind: 'year-page',
    ...overrides,
  };
}

export function coverage(overrides: Partial<HintCoverage> = {}): HintCoverage {
  return {
    minimum: 5,
    pending: 1,
    gaps: 2,
    rows: [
      {
        year: 1976,
        cells: [
          { category: 'MUSIC', fr: 10, en: 10 },
          { category: 'SCIENCE', fr: 4, en: 5 },
        ],
      },
    ],
    ...overrides,
  };
}
