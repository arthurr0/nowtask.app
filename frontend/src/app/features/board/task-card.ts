import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { PrefsService } from '../../core/prefs.service';
import { isOverdue, shortDate } from '../../core/format';
import type { TaskDto } from '../../core/api-types';
import { WorkspaceStore } from '../../data/workspace.store';
import { Avatar } from '../../ui/avatar';
import { Icon } from '../../ui/icon';
import { Menu, type MenuItem } from '../../ui/menu';

const UNASSIGNED = '__none__';

@Component({
  selector: 'app-task-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, Avatar, Menu],
  template: `
    <article
      class="flex cursor-pointer flex-col gap-2.5 rounded-card border bg-surface shadow-card"
      [style.padding]="'var(--card-pad)'"
      [class.border-accent]="selected()"
      [class.ring-1]="selected()"
      [class.ring-accent]="selected()"
      [class.border-line]="!selected()"
    >
      <div class="flex items-center gap-2">
        <span class="font-mono text-[11px] text-ink-3">{{ task().key }}</span>
        <span class="flex-1"></span>
        @if (task().priority === 'high' || task().priority === 'critical') {
          <ui-icon
            name="flag"
            [size]="13"
            class="text-warn"
            [attr.title]="t('task.highPriority')"
          />
        }
        @if (task().automated) {
          <ui-icon
            name="bolt"
            [size]="13"
            class="text-accent"
            [attr.title]="t('nav.automations')"
          />
        }
      </div>

      <h3 class="text-[13px] leading-[1.45] font-medium text-pretty">{{ task().title }}</h3>

      @if (task().labels.length) {
        <div class="flex flex-wrap gap-1.5">
          @for (label of task().labels; track label) {
            <span class="rounded-full border border-line px-2 py-0.5 text-[11px] text-ink-2">{{
              label
            }}</span>
          }
        </div>
      }

      @if (task().progress > 0 && task().progress < 100) {
        <div class="h-[3px] overflow-hidden rounded-full bg-surface-3">
          <div class="h-[3px] bg-ink-2" [style.width.%]="task().progress"></div>
        </div>
      }

      <div class="flex items-center gap-2.5 text-ink-3">
        @if (prefs.avatarsOnCards()) {
          @if (interactive()) {
            <ui-menu
              [items]="assigneeItems()"
              triggerClass="hoverable flex items-center justify-center rounded-full"
              triggerHeight="22px"
              ariaLabel="list.assign"
              (selected)="onAssign($event)"
              (click)="$event.stopPropagation()"
            >
              <ui-avatar [user]="assignee()" [size]="22" />
            </ui-menu>
          } @else {
            <ui-avatar [user]="assignee()" [size]="22" />
          }
        }
        @if (task().dueDate) {
          <span class="flex items-center gap-1 font-mono text-[11px]" [class.text-warn]="overdue()">
            <ui-icon name="clock" [size]="13" />
            {{ due() }}
          </span>
        }
        <span class="flex-1"></span>
        @if (task().subtasksTotal) {
          <span class="flex items-center gap-1 font-mono text-[11px]">
            <ui-icon name="check" [size]="13" />
            {{ task().subtasksDone }}/{{ task().subtasksTotal }}
          </span>
        }
        @if (task().commentCount) {
          <span class="flex items-center gap-1 font-mono text-[11px]">
            <ui-icon name="message" [size]="13" />
            {{ task().commentCount }}
          </span>
        }
      </div>
    </article>
  `,
})
export class TaskCard {
  private readonly store = inject(WorkspaceStore);
  protected readonly prefs = inject(PrefsService);
  protected readonly t = inject(I18nService).t;

  readonly task = input.required<TaskDto>();
  readonly selected = input(false);
  readonly interactive = input(false);

  readonly assign = output<string | null>();

  protected readonly assignee = computed(() => this.store.user(this.task().assigneeId));
  protected readonly due = computed(() => shortDate(this.task().dueDate));
  protected readonly overdue = computed(() => isOverdue(this.task().dueDate, this.store.today));

  protected readonly assigneeItems = computed<MenuItem[]>(() => {
    const current = this.task().assigneeId;
    const items: MenuItem[] = this.store.activeMembers().map((user) => ({
      id: user.id,
      label: user.name,
      icon: 'user',
      checked: current === user.id,
    }));
    items.push({
      id: UNASSIGNED,
      label: 'common.unassigned',
      separatorBefore: true,
      checked: current === null,
    });
    return items;
  });

  protected onAssign(item: MenuItem): void {
    const assigneeId = item.id === UNASSIGNED ? null : item.id;
    if (assigneeId === this.task().assigneeId) return;
    this.assign.emit(assigneeId);
  }
}
