import { TestBed } from '@angular/core/testing';
import { HintCategory } from '../models';
import { LeadIcon } from './lead-icon';

const CATEGORIES: HintCategory[] = ['MUSIC', 'FILM', 'SPORT', 'SCIENCE', 'WORLD', 'CELEBRITIES'];

describe('LeadIcon', () => {
  function render(category: HintCategory, size?: number) {
    const fixture = TestBed.createComponent(LeadIcon);
    fixture.componentRef.setInput('category', category);
    if (size !== undefined) fixture.componentRef.setInput('size', size);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it.each(CATEGORIES)('draws one decorative icon for %s', (category) => {
    const host = render(category);

    expect(host.querySelectorAll('svg')).toHaveLength(1);
    expect(host.getAttribute('aria-hidden')).toBe('true');
  });

  it('draws a different icon for each category', () => {
    const shapes = CATEGORIES.map((category) => render(category).querySelector('svg')!.innerHTML);

    expect(new Set(shapes).size).toBe(CATEGORIES.length);
  });

  it('takes the size asked', () => {
    expect(render('MUSIC', 14).querySelector('svg')!.getAttribute('width')).toBe('14');
  });
});
