import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Page, PageWidth } from './page';

@Component({
  imports: [Page],
  template: `<app-page [width]="width()"><h1>Title</h1></app-page>`,
})
class Host {
  readonly width = signal<PageWidth>('default');
}

describe('Page', () => {
  it('centres its content at the width of its kind of page', () => {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const page = (fixture.nativeElement as HTMLElement).querySelector('app-page')!;

    expect(page.querySelector('h1')).not.toBeNull();
    expect(page.className).toContain('mx-auto');
    expect(page.className).toContain('max-w-[824px]');

    fixture.componentInstance.width.set('narrow');
    fixture.detectChanges();

    expect(page.className).toContain('max-w-[452px]');
    expect(page.className).not.toContain('max-w-[824px]');
  });
});
