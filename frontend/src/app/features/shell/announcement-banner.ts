import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import {
  announcementIconTone,
  announcementKey,
  announcementTone,
  announcementVisible,
  clearDismissedAnnouncement,
  readDismissedAnnouncement,
  writeDismissedAnnouncement,
} from '../../core/announcement';
import { I18nService } from '../../core/i18n/i18n.service';
import { WorkspaceStore } from '../../data/workspace.store';
import { Icon } from '../../ui/icon';

@Component({
  selector: 'app-announcement-banner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  host: { class: 'block flex-none' },
  template: `
    @if (current(); as announcement) {
      <div
        role="status"
        class="flex items-start gap-2.5 border-b px-4 py-2 text-[13px]"
        [class]="tone(announcement.level)"
      >
        <ui-icon
          name="alert"
          [size]="15"
          class="mt-px flex-none"
          [class]="iconTone(announcement.level)"
        />
        <span class="min-w-0 flex-1 break-words">
          <span class="sr-only">{{ t('instance.banner.label') }}: </span>{{ announcement.text }}
        </span>
        <button
          type="button"
          class="hoverable -my-1.5 -mr-1.5 flex h-8 w-8 flex-none items-center justify-center rounded-[6px] text-ink-2"
          [attr.aria-label]="t('instance.banner.dismiss')"
          (click)="dismiss()"
        >
          <ui-icon name="x" [size]="14" />
        </button>
      </div>
    }
  `,
})
export class AnnouncementBanner {
  private readonly store = inject(WorkspaceStore);
  protected readonly t = inject(I18nService).t;

  protected readonly tone = announcementTone;
  protected readonly iconTone = announcementIconTone;

  private readonly dismissed = signal<string | null>(readDismissedAnnouncement());

  protected readonly current = computed(() => {
    const announcement = this.store.announcement();
    if (!announcementVisible(announcement)) return null;
    return announcementKey(announcement) === this.dismissed() ? null : announcement;
  });

  constructor() {
    effect(() => {
      if (!this.store.ready() || this.store.announcement() !== null) return;
      untracked(() => {
        clearDismissedAnnouncement();
        this.dismissed.set(null);
      });
    });
  }

  protected dismiss(): void {
    const announcement = this.current();
    if (!announcement) return;
    const key = announcementKey(announcement);
    writeDismissedAnnouncement(key);
    this.dismissed.set(key);
  }
}
