import { PendingPhoto } from './models';

/** A pending photo of a deceased person. */
export function pendingPhoto(id: string, overrides: Partial<PendingPhoto> = {}): PendingPhoto {
  return {
    photoId: id,
    person: {
      id: `person-${id}`,
      wikidataId: 'Q1',
      nameEn: `Person ${id}`,
      nameFr: null,
      birthDate: '1950-03-10',
      deathDate: '2010-01-01',
      sitelinks: 42,
    },
    takenFrom: '1976-07-14',
    takenTo: '1976-07-14',
    ageAtPhoto: 26,
    dateSource: 'BOTH',
    commonsFile: `Photo ${id}.jpg`,
    sourceUrl: `https://commons.wikimedia.org/wiki/File:Photo_${id}.jpg`,
    licenseShortName: 'CC BY-SA 4.0',
    licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0',
    authorText: 'Someone',
    otherPhotos: [],
    ...overrides,
  };
}
