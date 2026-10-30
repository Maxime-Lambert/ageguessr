/** Mirrors of the /api/admin/hints responses (ageguessr-api features/hints). */

export type HintReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type HintCategory = 'MUSIC' | 'FILM' | 'SPORT' | 'SCIENCE' | 'WORLD' | 'CELEBRITIES';
export type HintAudience = 'ALL' | 'FR' | 'EN';

export interface PendingHint {
  hintId: string;
  category: HintCategory;
  year: number;
  audience: HintAudience;
  textEn: string | null;
  textFr: string | null;
  sourceUrl: string;
  sourceKind: string;
}

export interface PendingHints {
  totalPending: number;
  items: PendingHint[];
}

/** A null or absent text leaves it as it is. */
export interface HintReview {
  decision: HintReviewStatus;
  textEn?: string;
  textFr?: string;
}

export interface HintReviewResult {
  hintId: string;
  reviewStatus: HintReviewStatus;
  textEn: string | null;
  textFr: string | null;
}

export interface CoverageCell {
  category: HintCategory;
  fr: number;
  en: number;
}

export interface CoverageRow {
  year: number;
  cells: CoverageCell[];
}

export interface HintCoverage {
  minimum: number;
  pending: number;
  gaps: number;
  rows: CoverageRow[];
}
