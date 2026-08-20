import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input } from '@angular/core';
import { Router } from '@angular/router';
import { I18nService } from '../../core/i18n/i18n.service';
import { dateTime } from '../../core/format';
import { AgentsStore, MetricsStore } from '../../data/feature.stores';
import { WorkspaceStore } from '../../data/workspace.store';
import { Icon } from '../../ui/icon';
import { Menu, type MenuItem } from '../../ui/menu';
import { PageState } from '../../ui/page-state';
import { Topbar } from '../../ui/topbar';
import { ViewControls } from '../../ui/view-controls';

const CHART = { left: 40, right: 700, top: 10, bottom: 166, max: 120 };

@Component({
  selector: 'app-dashboard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, Menu, Topbar, ViewControls, PageState],
  templateUrl: './dashboard.html',
})
export class Dashboard implements OnInit {
  protected readonly store = inject(WorkspaceStore);
  protected readonly metrics = inject(MetricsStore);
  protected readonly agents = inject(AgentsStore);
  protected readonly t = inject(I18nService).t;
  private readonly router = inject(Router);

  readonly mode = input<'overview' | 'reports'>('overview');

  protected readonly overview = this.metrics.overview;

  protected readonly dateTime = dateTime;

  protected readonly recentAgentActivity = computed(() => this.agents.activity().slice(0, 3));

  ngOnInit(): void {
    void this.metrics.load();
    void this.agents.load();
  }

  openAgents(): void {
    void this.router.navigate(['/app/agents']);
  }

  reload(): void {
    void this.metrics.load();
  }

  protected readonly periods: readonly number[] = [7, 14, 30, 90];

  periodItems(): MenuItem[] {
    return this.periods.map((days) => ({
      id: String(days),
      label: this.t('dash.lastDays', { days }),
      checked: this.metrics.days() === days,
    }));
  }

  periodLabel(): string {
    return this.t('dash.lastDays', { days: this.metrics.days() });
  }

  setPeriod(item: MenuItem): void {
    void this.metrics.load(Number.parseInt(item.id, 10));
  }

  openRule(ruleId: string): void {
    void this.router.navigate(['/app/automations'], { queryParams: { rule: ruleId } });
  }

  openAutomations(): void {
    void this.router.navigate(['/app/automations']);
  }

  protected readonly cycleTime = computed(() => {
    const value = this.overview()?.averageCycleTimeDays;
    return value === null || value === undefined ? '—' : value.toFixed(1).replace('.', ',');
  });

  private readonly points = computed(() => {
    const data = this.overview()?.burndown ?? [];
    if (data.length === 0) return [];
    const step = (CHART.right - CHART.left) / Math.max(1, data.length - 1);
    const max = Math.max(...data.map((point) => point.ideal), 1);

    return data.map((point, index) => ({
      ...point,
      label: point.day.slice(8, 10) + '.' + point.day.slice(5, 7),
      x: CHART.left + index * step,
      yActual:
        point.remaining === null
          ? null
          : CHART.top + ((max - point.remaining) / max) * (CHART.bottom - CHART.top),
      yIdeal: CHART.top + ((max - point.ideal) / max) * (CHART.bottom - CHART.top),
    }));
  });

  protected readonly actualPath = computed(() =>
    this.points()
      .filter((point) => point.yActual !== null)
      .map(
        (point, index) =>
          `${index === 0 ? 'M' : 'L'}${point.x.toFixed(1)} ${point.yActual!.toFixed(1)}`,
      )
      .join(' '),
  );

  protected readonly idealPath = computed(() => {
    const list = this.points();
    if (list.length === 0) return '';
    const first = list[0];
    const last = list[list.length - 1];
    return `M${first.x} ${first.yIdeal.toFixed(1)} L${last.x} ${last.yIdeal.toFixed(1)}`;
  });

  protected readonly lastPoint = computed(() => {
    const withValue = this.points().filter((point) => point.yActual !== null);
    return withValue[withValue.length - 1] ?? null;
  });

  protected readonly gridLines = computed(() => {
    const max = Math.max(...this.points().map((point) => point.ideal), 120);
    return [0, 0.25, 0.5, 0.75, 1].map((fraction) => ({
      value: Math.round(max * fraction),
      y: CHART.top + (1 - fraction) * (CHART.bottom - CHART.top),
    }));
  });

  protected readonly xLabels = computed(() =>
    this.points().filter((_, index) => index % 2 === 0 || index === this.points().length - 1),
  );

  protected readonly maxThroughput = computed(() =>
    Math.max(...(this.overview()?.throughput ?? []).map((week) => week.completed), 1),
  );

  barHeight(value: number): number {
    return Math.round((value / this.maxThroughput()) * 98);
  }

  workloadPercent(points: number, capacity: number): number {
    if (capacity <= 0) return 0;
    return Math.min(100, Math.round((points / capacity) * 100));
  }
}
