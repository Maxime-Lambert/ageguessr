import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { CasePhoto } from './case-photo';

@Component({
  imports: [CasePhoto],
  template: `
    <app-case-photo
      src="/api/images/p1"
      alt="The person"
      caption="Jane Doe"
      (failed)="failures = failures + 1"
    >
      <small>Photo: Someone</small>
    </app-case-photo>
  `,
})
class Host {
  failures = 0;
}

describe('CasePhoto', () => {
  it('shows the photo, uncropped and aged, with its caption and credit', () => {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    const image = page.querySelector('img')!;

    expect(image.getAttribute('src')).toBe('/api/images/p1');
    expect(image.getAttribute('alt')).toBe('The person');
    expect(image.classList).toContain('archive-photo');
    expect(image.className).not.toContain('object-cover');
    expect(page.querySelector('figcaption')?.textContent).toContain('Jane Doe');
    expect(page.querySelector('figcaption small')?.textContent).toBe('Photo: Someone');
  });

  it('tells when the photo does not load', () => {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();

    (fixture.nativeElement as HTMLElement).querySelector('img')!.dispatchEvent(new Event('error'));

    expect(fixture.componentInstance.failures).toBe(1);
  });
});
