import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Dropdown } from './dropdown';

@Component({
  imports: [Dropdown],
  template: `
    <app-dropdown label="Preferences">
      <span appDropdownTrigger>⚙</span>
      <a href="#account">Account</a>
      <button type="button">Dark</button>
    </app-dropdown>
    <p id="outside">Elsewhere</p>
  `,
})
class Host {}

describe('Dropdown', () => {
  function render() {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    document.body.appendChild(page);
    const trigger = page.querySelector<HTMLButtonElement>('app-dropdown > button')!;
    const panel = page.querySelector<HTMLElement>(`#${trigger.getAttribute('aria-controls')}`)!;
    const click = (el: Element) => {
      el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
      fixture.detectChanges();
    };
    return { fixture, page, trigger, panel, click };
  }

  afterEach(() => document.body.replaceChildren());

  it('is a named, collapsed disclosure until clicked', () => {
    const { trigger, panel, click } = render();

    expect(trigger.getAttribute('aria-label')).toBe('Preferences');
    expect(trigger.getAttribute('aria-expanded')).toBe('false');
    expect(panel.hidden).toBe(true);

    click(trigger);

    expect(trigger.getAttribute('aria-expanded')).toBe('true');
    expect(panel.hidden).toBe(false);
  });

  it('stays open on a click inside the panel, closes on a click outside', () => {
    const { page, trigger, panel, click } = render();
    click(trigger);

    click(panel.querySelector('button')!);
    expect(panel.hidden).toBe(false);

    click(page.querySelector('#outside')!);
    expect(panel.hidden).toBe(true);
  });

  it('closes when a link is followed', () => {
    const { trigger, panel, click } = render();
    click(trigger);

    click(panel.querySelector('a')!);

    expect(panel.hidden).toBe(true);
  });

  it('stays open when a link cancels its own navigation', () => {
    const { trigger, panel, click } = render();
    click(trigger);
    const link = panel.querySelector('a')!;
    link.addEventListener('click', (event) => event.preventDefault());

    click(link);

    expect(panel.hidden).toBe(false);
  });

  it('closes on Escape and gives the focus back to its button', () => {
    const { fixture, trigger, panel, click } = render();
    click(trigger);
    panel.querySelector('button')!.focus();

    panel
      .querySelector('button')!
      .dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();

    expect(panel.hidden).toBe(true);
    expect(document.activeElement).toBe(trigger);
  });
});
