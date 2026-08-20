import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'ui-topbar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header
      class="flex min-h-14 flex-none flex-wrap items-center gap-x-3.5 gap-y-2 border-b border-line bg-surface px-4 py-2.5 lg:flex-nowrap lg:px-5 lg:py-0"
    >
      <ng-content />
    </header>
  `,
})
export class Topbar {}
