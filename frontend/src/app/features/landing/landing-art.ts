import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Icon } from '../../ui/icon';

export type LandingArtKind = 'views' | 'fields' | 'rules' | 'permissions' | 'theme' | 'data';

const VIEW_TABS = [
  { icon: 'board', label: 'Board', active: true },
  { icon: 'list', label: 'List', active: false },
  { icon: 'timeline', label: 'Timeline', active: false },
  { icon: 'calendar', label: 'Calendar', active: false },
] as const;

const VIEW_COLUMNS = [
  {
    label: 'To do',
    dot: 'bg-line-strong',
    cards: [
      { key: 'NT-142', title: 'Customer database migration', high: true },
      { key: 'NT-151', title: 'Permissions audit', high: false },
    ],
  },
  {
    label: 'In progress',
    dot: 'bg-signal',
    cards: [
      { key: 'NT-138', title: 'Fixes in the CSV export', high: false },
      { key: 'NT-147', title: 'Calendar integration', high: false },
    ],
  },
  {
    label: 'Review',
    dot: 'bg-accent',
    cards: [{ key: 'NT-131', title: 'New intake form', high: true }],
  },
] as const;

const STATUS_ROWS = [
  { label: 'Backlog', dot: 'bg-line-strong', wip: null, category: 'Open' },
  { label: 'In progress', dot: 'bg-signal', wip: '3 / 4', category: 'Active' },
  { label: 'Review', dot: 'bg-accent', wip: '2 / 2', category: 'Active' },
  { label: 'Done', dot: 'bg-done', wip: null, category: 'Closed' },
] as const;

const FIELD_CHIPS = [
  'Priority · select',
  'Budget · number',
  'Due · date',
  'Client · text',
] as const;

const PERMISSION_ROWS = [
  { field: 'Title', access: 'Everyone', icon: 'eye', locked: false },
  { field: 'Client contact', access: 'Members only', icon: 'users', locked: false },
  { field: 'Budget', access: 'Hidden from guests', icon: 'lock', locked: true },
] as const;

const ROLES = ['Admin', 'Member', 'Guest'] as const;

const ACCENTS = [
  { id: 'graphite', color: 'oklch(0.78 0.008 265)' },
  { id: 'blue', color: 'oklch(0.705 0.08 252)' },
  { id: 'clay', color: 'oklch(0.705 0.08 42)' },
  { id: 'moss', color: 'oklch(0.705 0.08 146)' },
  { id: 'plum', color: 'oklch(0.705 0.08 318)' },
] as const;

const DENSITIES = ['Compact', 'Cozy', 'Roomy'] as const;
const RADII = ['0', '4', '8', '14'] as const;

const EXPORT_ROWS = [
  { label: 'tasks', value: '1 284' },
  { label: 'comments', value: '6 102' },
  { label: 'configuration', value: 'statuses, fields, rules' },
] as const;

@Component({
  selector: 'app-landing-art',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  host: { class: 'block' },
  template: `
    @switch (kind()) {
      @case ('views') {
        <div class="flex h-full flex-col gap-3 p-4">
          <div
            class="flex w-fit items-center gap-0.5 rounded-[7px] border border-line bg-surface-2 p-0.5"
          >
            @for (tab of viewTabs; track tab.label) {
              <span
                class="flex h-6 items-center gap-1.5 rounded-[5px] px-2 text-[11px]"
                [class.bg-inv]="tab.active"
                [class.text-inv-ink]="tab.active"
                [class.ink-crisp]="tab.active"
                [class.font-medium]="tab.active"
                [class.text-ink-3]="!tab.active"
              >
                <ui-icon [name]="tab.icon" [size]="12" />
                {{ tab.label }}
              </span>
            }
          </div>

          <div class="grid flex-1 grid-cols-3 gap-2.5">
            @for (column of viewColumns; track column.label) {
              <div class="flex flex-col gap-1.5">
                <div class="flex items-center gap-1.5 px-0.5">
                  <span class="h-1.5 w-1.5 rounded-full" [class]="column.dot"></span>
                  <span class="text-[11px] font-medium">{{ column.label }}</span>
                  <span class="font-mono text-[10px] text-ink-3">{{ column.cards.length }}</span>
                </div>
                @for (card of column.cards; track card.key) {
                  <div class="rounded-[6px] border border-line bg-surface p-2 shadow-card">
                    <div class="flex items-center gap-1.5">
                      <span class="font-mono text-[9px] text-ink-3">{{ card.key }}</span>
                      @if (card.high) {
                        <ui-icon name="flag" [size]="10" class="ml-auto text-warn" />
                      }
                    </div>
                    <p class="mt-1 text-[11px] leading-[1.35] font-medium">{{ card.title }}</p>
                  </div>
                }
              </div>
            }
          </div>
        </div>
      }

      @case ('fields') {
        <div class="flex h-full flex-col gap-3 p-4">
          <div class="overflow-hidden rounded-[7px] border border-line bg-surface">
            @for (row of statusRows; track row.label) {
              <div
                class="flex h-8 items-center gap-2 border-b border-line px-2.5 last:border-b-0"
                [class.bg-surface-2]="row.label === 'Review'"
              >
                <ui-icon name="grip" [size]="11" class="text-ink-3" />
                <span class="h-1.5 w-1.5 rounded-full" [class]="row.dot"></span>
                <span class="flex-1 text-[11px]">{{ row.label }}</span>
                @if (row.wip) {
                  <span class="font-mono text-[9px] text-ink-3">WIP {{ row.wip }}</span>
                }
                <span
                  class="rounded-full border border-line px-1.5 py-px font-mono text-[9px] text-ink-3"
                  >{{ row.category }}</span
                >
              </div>
            }
          </div>
          <div class="flex flex-wrap gap-1.5">
            @for (chip of fieldChips; track chip) {
              <span
                class="rounded-full border border-line bg-surface px-2 py-0.5 font-mono text-[10px] text-ink-2"
                >{{ chip }}</span
              >
            }
            <span
              class="flex items-center gap-1 rounded-full border border-dashed border-line-strong px-2 py-0.5 font-mono text-[10px] text-ink-3"
            >
              <ui-icon name="plus" [size]="10" />
              field
            </span>
          </div>
        </div>
      }

      @case ('rules') {
        <div class="relative flex h-full flex-col gap-2 p-4">
          <span
            aria-hidden="true"
            class="absolute top-8 bottom-8 left-[31px] w-px border-l border-dashed border-line-strong"
          ></span>
          <div class="relative flex items-center gap-2.5">
            <span
              class="flex h-7 w-7 flex-none items-center justify-center rounded-[6px] border border-line bg-surface text-ink-2"
            >
              <ui-icon name="play" [size]="12" />
            </span>
            <span class="rounded-[6px] border border-line bg-surface px-2.5 py-1.5 text-[11px]">
              <span class="text-ink-3">When</span> status changes to
              <span class="font-medium">Review</span>
            </span>
          </div>
          <div class="relative flex items-start gap-2.5">
            <span
              class="flex h-7 w-7 flex-none items-center justify-center rounded-[6px] border border-line bg-surface text-ink-2"
            >
              <ui-icon name="filter" [size]="12" />
            </span>
            <span class="flex flex-col gap-1 rounded-[6px] border border-line bg-surface p-1.5">
              <span class="px-1 text-[11px]">
                <span class="text-ink-3">If</span> priority is <span class="font-medium">High</span>
              </span>
              <span class="flex items-center gap-1.5 pl-1">
                <span class="rounded-full bg-surface-3 px-1.5 font-mono text-[9px] text-ink-2"
                  >or</span
                >
                <span class="text-[11px]"
                  >due date is within <span class="font-medium">2 days</span></span
                >
              </span>
            </span>
          </div>
          <div class="relative flex items-center gap-2.5">
            <span
              class="flex h-7 w-7 flex-none items-center justify-center rounded-[6px] border border-line-strong bg-inv text-inv-ink"
            >
              <ui-icon name="bolt" [size]="12" />
            </span>
            <span class="rounded-[6px] border border-line bg-surface px-2.5 py-1.5 text-[11px]">
              <span class="text-ink-3">Then</span> assign <span class="font-medium">Marta R.</span>
            </span>
          </div>
        </div>
      }

      @case ('permissions') {
        <div class="flex h-full flex-col gap-3 p-4">
          <div
            class="flex w-fit items-center gap-0.5 rounded-[7px] border border-line bg-surface-2 p-0.5"
          >
            @for (role of roles; track role) {
              <span
                class="flex h-6 items-center rounded-[5px] px-2.5 text-[11px]"
                [class.bg-inv]="role === 'Guest'"
                [class.text-inv-ink]="role === 'Guest'"
                [class.ink-crisp]="role === 'Guest'"
                [class.font-medium]="role === 'Guest'"
                [class.text-ink-3]="role !== 'Guest'"
                >{{ role }}</span
              >
            }
          </div>
          <div class="overflow-hidden rounded-[7px] border border-line bg-surface">
            @for (row of permissionRows; track row.field) {
              <div
                class="flex h-9 items-center gap-2.5 border-b border-line px-2.5 last:border-b-0"
              >
                <ui-icon
                  [name]="row.icon"
                  [size]="12"
                  [class.text-warn]="row.locked"
                  [class.text-ink-3]="!row.locked"
                />
                <span class="flex-1 text-[11px]" [class.text-ink-3]="row.locked">{{
                  row.field
                }}</span>
                <span
                  class="rounded-full px-1.5 py-px font-mono text-[9px]"
                  [class.bg-warn-soft]="row.locked"
                  [class.text-warn]="row.locked"
                  [class.border]="!row.locked"
                  [class.border-line]="!row.locked"
                  [class.text-ink-3]="!row.locked"
                  >{{ row.access }}</span
                >
              </div>
            }
          </div>
        </div>
      }

      @case ('theme') {
        <div class="flex h-full flex-col gap-2.5 p-4">
          <div class="flex items-center gap-3">
            <span class="w-14 text-[11px] text-ink-3">Theme</span>
            <span
              class="flex items-center gap-0.5 rounded-[7px] border border-line bg-surface-2 p-0.5"
            >
              <span class="flex h-6 items-center gap-1.5 rounded-[5px] px-2 text-[11px] text-ink-3">
                <ui-icon name="sun" [size]="11" />
                Light
              </span>
              <span
                class="ink-crisp flex h-6 items-center gap-1.5 rounded-[5px] bg-inv px-2 text-[11px] font-medium text-inv-ink"
              >
                <ui-icon name="moon" [size]="11" />
                Dark
              </span>
            </span>
          </div>
          <div class="flex items-center gap-3">
            <span class="w-14 text-[11px] text-ink-3">Accent</span>
            <span class="flex items-center gap-2">
              @for (accent of accents; track accent.id) {
                <span
                  class="flex h-6 w-6 items-center justify-center rounded-full border"
                  [class.border-ink]="accent.id === 'graphite'"
                  [class.border-transparent]="accent.id !== 'graphite'"
                >
                  <span class="h-3.5 w-3.5 rounded-full" [style.background]="accent.color"></span>
                </span>
              }
            </span>
          </div>
          <div class="flex items-center gap-3">
            <span class="w-14 text-[11px] text-ink-3">Density</span>
            <span
              class="flex items-center gap-0.5 rounded-[7px] border border-line bg-surface-2 p-0.5"
            >
              @for (density of densities; track density) {
                <span
                  class="flex h-6 items-center rounded-[5px] px-2 text-[11px]"
                  [class.bg-inv]="density === 'Cozy'"
                  [class.text-inv-ink]="density === 'Cozy'"
                  [class.ink-crisp]="density === 'Cozy'"
                  [class.font-medium]="density === 'Cozy'"
                  [class.text-ink-3]="density !== 'Cozy'"
                  >{{ density }}</span
                >
              }
            </span>
          </div>
          <div class="flex items-center gap-3">
            <span class="w-14 text-[11px] text-ink-3">Radius</span>
            <span class="flex items-center gap-1.5">
              @for (radius of radii; track radius) {
                <span
                  class="flex h-6 w-8 items-center justify-center border font-mono text-[10px]"
                  [style.borderRadius.px]="radius"
                  [class.border-ink]="radius === '8'"
                  [class.text-ink]="radius === '8'"
                  [class.border-line]="radius !== '8'"
                  [class.text-ink-3]="radius !== '8'"
                  >{{ radius }}</span
                >
              }
            </span>
          </div>
        </div>
      }

      @case ('data') {
        <div class="flex h-full flex-col gap-3 p-4">
          <div
            class="flex items-center gap-2.5 rounded-[7px] border border-line bg-surface px-3 py-2"
          >
            <span
              class="flex h-7 w-7 flex-none items-center justify-center rounded-[6px] bg-surface-2 text-ink-2"
            >
              <ui-icon name="archive" [size]="13" />
            </span>
            <span class="flex min-w-0 flex-1 flex-col">
              <span class="truncate font-mono text-[11px]">nowtask-export-2026-09.json</span>
              <span class="font-mono text-[9px] text-ink-3">2.4 MB · ready</span>
            </span>
            <span class="flex items-center gap-1 text-[11px] text-ink-2">
              <ui-icon name="down" [size]="12" />
              Download
            </span>
          </div>
          <div class="grid grid-cols-3 gap-1.5">
            @for (row of exportRows; track row.label) {
              <span
                class="flex flex-col gap-0.5 rounded-[6px] border border-line bg-surface px-2 py-1.5"
              >
                <span class="font-mono text-[9px] text-ink-3">{{ row.label }}</span>
                <span class="truncate text-[11px] font-medium">{{ row.value }}</span>
              </span>
            }
          </div>
          <div
            class="flex items-center gap-2 rounded-[6px] bg-surface-2 px-2.5 py-1.5 font-mono text-[10px]"
          >
            <span class="text-done-ink">GET</span>
            <span class="flex-1 truncate text-ink-2">/api/v1/tasks?status=review</span>
            <span class="text-ink-3">200</span>
          </div>
        </div>
      }
    }
  `,
})
export class LandingArt {
  readonly kind = input.required<LandingArtKind>();

  protected readonly viewTabs = VIEW_TABS;
  protected readonly viewColumns = VIEW_COLUMNS;
  protected readonly statusRows = STATUS_ROWS;
  protected readonly fieldChips = FIELD_CHIPS;
  protected readonly permissionRows = PERMISSION_ROWS;
  protected readonly roles = ROLES;
  protected readonly accents = ACCENTS;
  protected readonly densities = DENSITIES;
  protected readonly radii = RADII;
  protected readonly exportRows = EXPORT_ROWS;
}
