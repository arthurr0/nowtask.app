import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { ToastService } from '../../ui/toast.service';
import { Icon } from '../../ui/icon';
import { Switch } from '../../ui/switch';
import { Topbar } from '../../ui/topbar';
import { ViewControls } from '../../ui/view-controls';

@Component({
  selector: 'app-system',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, Switch, Topbar, ViewControls],
  templateUrl: './system.html',
})
export class System {
  protected readonly t = inject(I18nService).t;

  private readonly toast = inject(ToastService);

  demo(label: string): void {
    this.toast.info(this.t('system.demoClick', { name: this.t(label) }));
  }

  protected readonly lightRamp = [
    { token: 'bg', value: 'oklch(0.985 0.002 265)' },
    { token: 'surface', value: 'oklch(1 0 0)' },
    { token: 'surface-2', value: 'oklch(0.966 0.003 265)' },
    { token: 'surface-3', value: 'oklch(0.938 0.004 265)' },
    { token: 'line', value: 'oklch(0.906 0.005 265)' },
    { token: 'line-strong', value: 'oklch(0.842 0.006 265)' },
    { token: 'ink-3', value: 'oklch(0.645 0.006 265)' },
    { token: 'ink-2', value: 'oklch(0.5 0.007 265)' },
    { token: 'ink', value: 'oklch(0.24 0.008 265)' },
  ];

  protected readonly darkRamp = [
    { token: 'bg', value: 'oklch(0.163 0.004 265)' },
    { token: 'surface', value: 'oklch(0.204 0.004 265)' },
    { token: 'surface-2', value: 'oklch(0.238 0.005 265)' },
    { token: 'surface-3', value: 'oklch(0.272 0.005 265)' },
    { token: 'line', value: 'oklch(0.301 0.006 265)' },
    { token: 'line-strong', value: 'oklch(0.382 0.007 265)' },
    { token: 'ink-3', value: 'oklch(0.562 0.005 265)' },
    { token: 'ink-2', value: 'oklch(0.702 0.005 265)' },
    { token: 'ink', value: 'oklch(0.945 0.003 265)' },
  ];

  protected readonly accents = [
    { label: 'grafit', value: 'oklch(0.46 0.012 265)' },
    { label: '252', value: 'oklch(0.58 0.09 252)' },
    { label: '42', value: 'oklch(0.58 0.09 42)' },
    { label: '146', value: 'oklch(0.58 0.09 146)' },
    { label: '318', value: 'oklch(0.58 0.09 318)' },
  ];

  protected readonly spacing = [4, 8, 12, 16, 20, 24, 32, 40];

  protected readonly radii = [
    { px: 4, label: '4' },
    { px: 6, label: '6 · pola' },
    { px: 9, label: '9 · karty' },
    { px: 12, label: '12' },
    { px: 999, label: '999 · chipy' },
  ];

  protected readonly icons = [
    'home',
    'board',
    'list',
    'timeline',
    'calendar',
    'bolt',
    'chart',
    'search',
    'filter',
    'sliders',
    'users',
    'lock',
    'clock',
    'message',
    'flag',
    'check',
    'plus',
    'x',
    'trash',
    'globe',
    'sun',
    'moon',
    'dots',
    'chevron-down',
  ];
}
