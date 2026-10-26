import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PersonSearchResult } from '../models';
import { Exclusion } from './exclusion';

const JANE: PersonSearchResult = {
  id: 'person-1',
  wikidataId: 'Q7',
  nameEn: 'Jane Doe',
  nameFr: null,
  birthDate: '1950-03-10',
  deathDate: null,
  excluded: false,
  photoCount: 2,
};

describe('Exclusion', () => {
  let fixture: ComponentFixture<Exclusion>;
  let http: HttpTestingController;
  let page: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Exclusion);
    page = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function search(text: string): void {
    const input = page.querySelector('input') as HTMLInputElement;
    input.value = text;
    input.dispatchEvent(new Event('input'));
    page.querySelector('form')?.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  function button(label: string): HTMLButtonElement | undefined {
    return [...page.querySelectorAll('button')].find((b) => b.textContent?.includes(label));
  }

  it('finds people by name', () => {
    search(' jane ');
    http.expectOne('/api/admin/people?query=jane').flush({ people: [JANE] });
    fixture.detectChanges();

    expect(page.textContent).toContain('Jane Doe');
    expect(page.textContent).toContain('2 photo(s)');
  });

  it('refuses a one-letter search', () => {
    search('j');

    http.expectNone((req) => req.url.startsWith('/api/admin/people'));
    expect(page.textContent).toContain('Type at least 2 characters.');
  });

  it('excludes only after a second, explicit click, then tells the page', () => {
    const emitted: string[] = [];
    fixture.componentInstance.excluded.subscribe((id) => emitted.push(id));
    search('Jane');
    http.expectOne('/api/admin/people?query=Jane').flush({ people: [JANE] });
    fixture.detectChanges();

    page.querySelector<HTMLButtonElement>('button[aria-label="Exclude Jane Doe"]')?.click();
    fixture.detectChanges();
    http.expectNone('/api/admin/people/person-1/exclusion');

    button('Delete 2 photo(s) and exclude')?.click();
    http
      .expectOne('/api/admin/people/person-1/exclusion')
      .flush({ personId: 'person-1', deletedPhotos: 2 });
    fixture.detectChanges();

    expect(emitted).toEqual(['person-1']);
    expect(page.querySelector('[role="status"]')?.textContent).toContain(
      'Jane Doe is excluded: 2 photo(s) deleted.',
    );
    expect(page.textContent).toContain('Excluded');
  });

  it('can cancel before excluding', () => {
    search('Jane');
    http.expectOne('/api/admin/people?query=Jane').flush({ people: [JANE] });
    fixture.detectChanges();

    page.querySelector<HTMLButtonElement>('button[aria-label="Exclude Jane Doe"]')?.click();
    fixture.detectChanges();
    button('Cancel')?.click();
    fixture.detectChanges();

    expect(button('Delete')).toBeUndefined();
  });

  it('says when nobody matches', () => {
    search('Nobody');
    http.expectOne('/api/admin/people?query=Nobody').flush({ people: [] });
    fixture.detectChanges();

    expect(page.textContent).toContain('Nobody matches.');
  });
});
