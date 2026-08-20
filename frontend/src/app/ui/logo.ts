import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

@Component({
  selector: 'ui-logo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span
      class="inline-flex items-center justify-center"
      [class.bg-inv]="tile()"
      [style.width.px]="size()"
      [style.height.px]="size()"
      [style.borderRadius.px]="tile() ? radius() : 0"
    >
      <svg
        [attr.width]="glyph()"
        [attr.height]="glyph()"
        viewBox="0 0 32 32"
        aria-hidden="true"
        focusable="false"
      >
        <g [attr.fill]="tile() ? 'var(--c-inv-ink)' : 'var(--c-ink)'">
          <rect x="6" y="16" width="5" height="10" rx="2.5" opacity="0.38" />
          <rect x="13.5" y="13" width="5" height="13" rx="2.5" />
          <rect x="21" y="6" width="5" height="20" rx="2.5" />
        </g>
        <circle
          cx="16"
          cy="7.6"
          r="3.1"
          [attr.fill]="tile() ? 'var(--c-brand-inv)' : 'var(--c-brand)'"
        />
      </svg>
    </span>
  `,
  styles: `
    :host {
      display: inline-flex;
      flex: none;
    }
    svg {
      display: block;
    }
  `,
})
export class Logo {
  readonly size = input(24);
  readonly radius = input(6);
  readonly tile = input(true);
  readonly glyph = computed(() => Math.round(this.size() * (this.tile() ? 0.7 : 1)));
}
