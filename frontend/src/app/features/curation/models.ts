/** Mirrors of the /api/admin/** responses (ageguessr-api features/people). */

export type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type ReviewDecision = ReviewStatus;
export type PhotoDateSource = 'STRUCTURED_DATA' | 'INFORMATION_TEMPLATE' | 'BOTH';

export interface PendingPerson {
  id: string;
  wikidataId: string;
  nameEn: string | null;
  nameFr: string | null;
  birthDate: string;
  deathDate: string | null;
  sitelinks: number;
}

export interface OtherPhoto {
  photoId: string;
  takenFrom: string;
  ageAtPhoto: number;
  reviewStatus: ReviewStatus;
}

export interface PendingPhoto {
  photoId: string;
  person: PendingPerson;
  takenFrom: string;
  takenTo: string;
  ageAtPhoto: number;
  dateSource: PhotoDateSource;
  commonsFile: string;
  sourceUrl: string;
  licenseShortName: string;
  licenseUrl: string | null;
  authorText: string | null;
  approvable: boolean;
  otherPhotos: OtherPhoto[];
}

export interface PendingPhotos {
  totalPending: number;
  items: PendingPhoto[];
}

export interface ReviewResult {
  photoId: string;
  reviewStatus: ReviewStatus;
}

export interface PersonSearchResult {
  id: string;
  wikidataId: string;
  nameEn: string | null;
  nameFr: string | null;
  birthDate: string;
  deathDate: string | null;
  excluded: boolean;
  photoCount: number;
}

export interface ExclusionResult {
  personId: string;
  deletedPhotos: number;
}

/** English name first, then French: the curation page is in English like the rest of the UI. */
export function displayName(person: { nameEn: string | null; nameFr: string | null }): string {
  return person.nameEn ?? person.nameFr ?? '(no name)';
}
