import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import type {
  AnnouncementLevel,
  InstanceSettingsPatch,
  InstanceSignupMode,
} from '../../core/api-types';
import { announcementIconTone, announcementTone } from '../../core/announcement';
import { AuthService } from '../../core/auth.service';
import { dateTime } from '../../core/format';
import { I18nService } from '../../core/i18n/i18n.service';
import { InstanceStore } from '../../data/instance.store';
import { DateField } from '../../ui/date-field';
import { Icon } from '../../ui/icon';
import { SelectField, type SelectOption } from '../../ui/select-field';
import { TextareaField } from '../../ui/textarea-field';
import { ToastService } from '../../ui/toast.service';
import { instanceErrorMessage, isoToLocalInput, localInputToIso } from './instance-utils';

const SIGNUP_MODES: readonly InstanceSignupMode[] = ['open', 'invite-only', 'sso-only'];
const LEVELS: readonly AnnouncementLevel[] = ['info', 'warning', 'critical'];
const ANNOUNCEMENT_MAX = 500;

@Component({
  selector: 'app-instance-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, SelectField, TextareaField, DateField],
  templateUrl: './instance-settings.html',
})
export class InstanceSettings implements OnInit {
  protected readonly store = inject(InstanceStore);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  protected readonly t = inject(I18nService).t;

  protected readonly announcementMax = ANNOUNCEMENT_MAX;
  protected readonly tone = announcementTone;
  protected readonly iconTone = announcementIconTone;

  protected readonly signupDraft = signal('');
  protected readonly textDraft = signal('');
  protected readonly levelDraft = signal<AnnouncementLevel>('info');
  protected readonly untilDraft = signal('');
  protected readonly saving = signal(false);
  protected readonly mailSending = signal(false);

  protected readonly signupOptions = computed<SelectOption[]>(() => {
    const env = this.store.settings()?.envSignupMode;
    return [
      {
        value: '',
        label: this.t('instance.settings.signupDefault', {
          mode: env ? this.t(`instance.signup.${env}`) : '',
        }),
      },
      ...SIGNUP_MODES.map((mode) => ({ value: mode, label: this.t(`instance.signup.${mode}`) })),
    ];
  });

  protected readonly levelOptions = computed<SelectOption[]>(() =>
    LEVELS.map((level) => ({ value: level, label: this.t(`instance.level.${level}`) })),
  );

  protected readonly effectiveLabel = computed(() => {
    const effective = this.store.settings()?.effectiveSignupMode;
    return effective
      ? this.t('instance.settings.signupEffective', {
          mode: this.t(`instance.signup.${effective}`),
        })
      : '';
  });

  protected readonly updatedLabel = computed(() => {
    const settings = this.store.settings();
    if (!settings?.updatedAt) return '';
    return this.t('instance.settings.updated', {
      when: dateTime(settings.updatedAt),
      who: settings.updatedBy?.name ?? this.t('instance.audit.system'),
    });
  });

  protected readonly dirty = computed(() => {
    const settings = this.store.settings();
    if (!settings) return false;
    const announcement = settings.announcement;
    const text = this.textDraft().trim();
    if (this.signupDraft() !== (settings.signupMode ?? '')) return true;
    if (!announcement) return text !== '';
    return (
      text !== announcement.text ||
      this.levelDraft() !== announcement.level ||
      this.untilDraft() !== isoToLocalInput(announcement.until)
    );
  });

  constructor() {
    effect(() => {
      const settings = this.store.settings();
      if (!settings) return;
      untracked(() => {
        this.signupDraft.set(settings.signupMode ?? '');
        this.textDraft.set(settings.announcement?.text ?? '');
        this.levelDraft.set(settings.announcement?.level ?? 'info');
        this.untilDraft.set(isoToLocalInput(settings.announcement?.until ?? null));
      });
    });
  }

  ngOnInit(): void {
    void this.load();
  }

  protected load(): Promise<void> {
    return this.store.loadSettings();
  }

  protected setLevel(value: string): void {
    this.levelDraft.set(value as AnnouncementLevel);
  }

  protected clearAnnouncement(): void {
    this.textDraft.set('');
    this.untilDraft.set('');
  }

  protected async save(): Promise<void> {
    if (!this.dirty() || this.saving()) return;
    const text = this.textDraft().trim();
    const patch: InstanceSettingsPatch = {
      signupMode: (this.signupDraft() || null) as InstanceSignupMode | null,
      announcement: text
        ? { text, level: this.levelDraft(), until: localInputToIso(this.untilDraft()) }
        : null,
    };
    this.saving.set(true);
    try {
      await this.store.saveSettings(patch);
      this.toast.success(this.t('instance.settings.saved'));
    } catch (error) {
      this.toast.error(instanceErrorMessage(error, this.t));
    } finally {
      this.saving.set(false);
    }
  }

  protected async sendTestMail(): Promise<void> {
    if (this.mailSending()) return;
    this.mailSending.set(true);
    try {
      await this.store.sendTestMail();
      this.toast.success(
        this.t('instance.settings.mailSent', { email: this.auth.user()?.email ?? '' }),
      );
    } catch (error) {
      this.toast.error(instanceErrorMessage(error, this.t));
    } finally {
      this.mailSending.set(false);
    }
  }
}
