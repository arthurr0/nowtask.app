import {
  ChangeDetectionStrategy,
  Component,
  Injectable,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { Router } from '@angular/router';
import { I18nService } from '../core/i18n/i18n.service';
import type { SearchResultDto } from '../data/workspace.store';
import { ViewState } from '../data/view-state';
import { WorkspaceStore } from '../data/workspace.store';
import { TaskComposer } from '../features/task-composer/task-composer';
import { Dialog } from './dialog';
import { Icon } from './icon';
import { PrefsService } from '../core/prefs.service';

interface PaletteEntry {
  id: string;
  group: string;
  label: string;
  hint: string;
  icon: string;
  run: () => void;
}

@Injectable({ providedIn: 'root' })
export class CommandPalette {
  private readonly visibleSignal = signal(false);
  readonly visible = this.visibleSignal.asReadonly();

  open(): void {
    this.visibleSignal.set(true);
  }

  close(): void {
    this.visibleSignal.set(false);
  }

  toggle(): void {
    this.visibleSignal.update((value) => !value);
  }
}

@Component({
  selector: 'ui-command-palette',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Dialog, Icon],
  host: {
    '(document:keydown)': 'onKeydown($event)',
  },
  template: `
    <ui-dialog
      [open]="palette.visible()"
      size="lg"
      title="palette.title"
      [showFooter]="false"
      (closed)="palette.close()"
    >
      <div class="flex flex-col gap-3">
        <div
          class="focus-ring flex h-10 items-center gap-2.5 rounded-field border border-line bg-surface-2 px-3"
        >
          <ui-icon name="search" [size]="16" class="text-ink-3" />
          <input
            class="min-w-0 flex-1 bg-transparent text-[13px] outline-none"
            [placeholder]="t('palette.placeholder')"
            [value]="query()"
            (input)="onInput($any($event.target).value)"
            (keydown)="onListKeydown($event)"
            autofocus
          />
          @if (loading()) {
            <span class="text-[11px] text-ink-3">{{ t('palette.searching') }}</span>
          }
        </div>

        <div class="flex max-h-[52vh] flex-col overflow-y-auto scroll-thin">
          @for (entry of entries(); track entry.id; let i = $index) {
            @if (i === 0 || entries()[i - 1].group !== entry.group) {
              <span class="kap px-2 pt-2.5 pb-1.5">{{ t(entry.group) }}</span>
            }
            <button
              type="button"
              class="flex items-center gap-2.5 rounded-[6px] px-2.5 py-2 text-left text-[13px]"
              [class.bg-surface-2]="active() === i"
              (mouseenter)="active.set(i)"
              (click)="choose(entry)"
            >
              <ui-icon [name]="entry.icon" [size]="15" class="flex-none text-ink-3" />
              <span class="min-w-0 flex-1 truncate">{{ entry.label }}</span>
              @if (entry.hint) {
                <span class="flex-none font-mono text-[11px] text-ink-3">{{ entry.hint }}</span>
              }
            </button>
          } @empty {
            <p class="px-2.5 py-6 text-center text-xs text-ink-3">{{ t('palette.empty') }}</p>
          }
        </div>
      </div>
    </ui-dialog>
  `,
})
export class CommandPaletteDialog {
  protected readonly palette = inject(CommandPalette);
  private readonly store = inject(WorkspaceStore);
  private readonly view = inject(ViewState);
  private readonly composer = inject(TaskComposer);
  private readonly prefs = inject(PrefsService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;

  protected readonly query = signal('');
  protected readonly active = signal(0);
  protected readonly loading = signal(false);
  private readonly results = signal<SearchResultDto | null>(null);

  private timer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    effect(() => {
      if (this.palette.visible()) {
        untracked(() => {
          this.query.set('');
          this.results.set(null);
          this.active.set(0);
        });
      }
    });
  }

  protected readonly entries = computed<PaletteEntry[]>(() => {
    const needle = this.query().trim().toLowerCase();
    const entries: PaletteEntry[] = [];

    for (const action of this.actions()) {
      if (!needle || action.label.toLowerCase().includes(needle)) entries.push(action);
    }

    const results = this.results();
    if (results) {
      for (const task of results.tasks) {
        entries.push({
          id: 'task-' + task.key,
          group: 'palette.tasks',
          label: task.title,
          hint: task.key,
          icon: 'check',
          run: () => void this.router.navigate(['/app/tasks', task.key]),
        });
      }
      for (const person of results.people) {
        entries.push({
          id: 'person-' + person.id,
          group: 'palette.people',
          label: person.name,
          hint: person.role?.name ?? '',
          icon: 'user',
          run: () => {
            this.view.reset();
            this.view.assigneeId.set(person.id);
            void this.router.navigate(['/app/list']);
          },
        });
      }
      for (const rule of results.rules) {
        entries.push({
          id: 'rule-' + rule.id,
          group: 'palette.rules',
          label: rule.name,
          hint: rule.enabled ? this.t('common.active') : this.t('common.draft'),
          icon: 'bolt',
          run: () =>
            void this.router.navigate(['/app/automations'], { queryParams: { rule: rule.id } }),
        });
      }
      for (const saved of results.views) {
        entries.push({
          id: 'view-' + saved.id,
          group: 'palette.views',
          label: saved.name ?? this.t(saved.code ?? ''),
          hint: String(saved.count),
          icon: 'save',
          run: () => {
            this.view.applyQuery(saved.query, saved.id);
            void this.router.navigate(['/app/list']);
          },
        });
      }
    }

    return entries;
  });

  private actions(): PaletteEntry[] {
    return [
      {
        id: 'action-new-task',
        group: 'palette.actions',
        label: this.t('common.newTask'),
        hint: 'N',
        icon: 'plus',
        run: () => this.composer.open(),
      },
      {
        id: 'action-board',
        group: 'palette.actions',
        label: this.t('nav.board'),
        hint: '',
        icon: 'board',
        run: () => void this.router.navigate(['/app/board']),
      },
      {
        id: 'action-list',
        group: 'palette.actions',
        label: this.t('nav.list'),
        hint: '',
        icon: 'list',
        run: () => void this.router.navigate(['/app/list']),
      },
      {
        id: 'action-timeline',
        group: 'palette.actions',
        label: this.t('nav.timeline'),
        hint: '',
        icon: 'timeline',
        run: () => void this.router.navigate(['/app/timeline']),
      },
      {
        id: 'action-automations',
        group: 'palette.actions',
        label: this.t('nav.automations'),
        hint: '',
        icon: 'bolt',
        run: () => void this.router.navigate(['/app/automations']),
      },
      {
        id: 'action-agents',
        group: 'palette.actions',
        label: this.t('nav.agents'),
        hint: '',
        icon: 'agent',
        run: () => void this.router.navigate(['/app/agents']),
      },
      {
        id: 'action-reports',
        group: 'palette.actions',
        label: this.t('nav.reports'),
        hint: '',
        icon: 'chart',
        run: () => void this.router.navigate(['/app/reports']),
      },
      {
        id: 'action-settings',
        group: 'palette.actions',
        label: this.t('nav.settings'),
        hint: '',
        icon: 'sliders',
        run: () => void this.router.navigate(['/app/settings']),
      },
      {
        id: 'action-organization',
        group: 'palette.actions',
        label: this.t('nav.organization'),
        hint: '',
        icon: 'users',
        run: () => void this.router.navigate(['/app/organization']),
      },
      {
        id: 'action-theme',
        group: 'palette.actions',
        label: this.t('common.toggleTheme'),
        hint: '',
        icon: 'moon',
        run: () => this.prefs.toggleTheme(),
      },
      {
        id: 'action-mine',
        group: 'palette.actions',
        label: this.t('filters.assignedToMe'),
        hint: '',
        icon: 'user',
        run: () => {
          const me = this.store.currentUser();
          if (!me) return;
          this.view.reset();
          this.view.assigneeId.set(me.id);
          void this.router.navigate(['/app/list']);
        },
      },
      {
        id: 'action-overdue',
        group: 'palette.actions',
        label: this.t('filters.overdue'),
        hint: '',
        icon: 'clock',
        run: () => {
          this.view.reset();
          this.view.overdueOnly.set(true);
          void this.router.navigate(['/app/list']);
        },
      },
    ];
  }

  protected onInput(value: string): void {
    this.query.set(value);
    this.active.set(0);
    if (this.timer) clearTimeout(this.timer);
    const needle = value.trim();
    if (needle.length < 2) {
      this.results.set(null);
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    this.timer = setTimeout(() => {
      void this.store
        .search(needle)
        .then((results) => this.results.set(results))
        .catch(() => this.results.set(null))
        .finally(() => this.loading.set(false));
    }, 180);
  }

  protected onKeydown(event: KeyboardEvent): void {
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      this.palette.toggle();
    }
  }

  protected onListKeydown(event: KeyboardEvent): void {
    const entries = this.entries();
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.active.update((index) => (entries.length ? (index + 1) % entries.length : 0));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.active.update((index) =>
        entries.length ? (index - 1 + entries.length) % entries.length : 0,
      );
    } else if (event.key === 'Enter') {
      event.preventDefault();
      const entry = entries[this.active()];
      if (entry) this.choose(entry);
    }
  }

  protected choose(entry: PaletteEntry): void {
    this.palette.close();
    entry.run();
  }
}
