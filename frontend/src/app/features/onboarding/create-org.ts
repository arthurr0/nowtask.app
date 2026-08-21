import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { I18nService } from '../../core/i18n/i18n.service';
import { OnboardingService } from '../../core/onboarding.service';
import { OrgService } from '../../core/org.service';
import { Icon } from '../../ui/icon';
import { WizardShell } from './wizard-shell';

function slugify(name: string): string {
  return name
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 40);
}

@Component({
  selector: 'app-create-org',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, Icon, WizardShell],
  template: `
    <app-wizard-shell
      [step]="1"
      [heading]="t('onboarding.orgTitle')"
      [subheading]="t('onboarding.orgSubtitle')"
    >
      <form class="flex flex-col gap-6" (submit)="submit($event)">
        <label class="flex flex-col gap-1.5">
          <span class="text-xs text-ink-2">{{ t('onboarding.orgName') }}</span>
          <input
            type="text"
            name="orgName"
            autocomplete="organization"
            class="h-11 rounded-card border border-line-strong bg-surface px-3.5 text-sm outline-none"
            [ngModel]="name()"
            (ngModelChange)="onNameChange($event)"
          />
        </label>

        <div class="flex flex-col gap-1.5">
          <span class="text-xs text-ink-2">{{ t('onboarding.orgSlug') }}</span>
          <div
            class="flex h-11 items-center gap-1 rounded-card border border-line-strong bg-surface px-3.5"
          >
            <span class="font-mono text-[13px] text-ink-3">nowtask.app/</span>
            <input
              type="text"
              name="orgSlug"
              class="min-w-0 flex-1 bg-transparent font-mono text-[13px] outline-none"
              [ngModel]="slug()"
              (ngModelChange)="onSlugChange($event)"
            />
            @if (slugState() === 'free') {
              <span class="flex items-center gap-1 text-[12px] text-done">
                <ui-icon name="check" [size]="14" />
                {{ t('onboarding.slugFree') }}
              </span>
            } @else if (slugState() === 'taken') {
              <span class="text-[12px] text-warn">{{ t('onboarding.slugTaken') }}</span>
            } @else if (slugState() === 'checking') {
              <span class="text-[12px] text-ink-3">{{ t('state.loading') }}</span>
            }
          </div>
          <p class="text-[12px] text-ink-3">{{ t('onboarding.orgHint') }}</p>
        </div>

        @if (error()) {
          <p
            class="flex items-start gap-2 rounded-card border border-warn bg-warn-soft px-3.5 py-2.5 text-[13px] text-warn"
          >
            <ui-icon name="alert" [size]="15" />
            <span>{{ error() }}</span>
          </p>
        }

        <div class="flex items-center justify-end gap-3">
          <button
            type="submit"
            class="flex h-[42px] items-center justify-center gap-2 rounded-card bg-inv px-5 text-sm font-medium text-inv-ink disabled:opacity-60"
            [disabled]="submitting() || !complete()"
          >
            {{ submitting() ? t('state.loading') : t('onboarding.next') }}
            <ui-icon name="chevron-right" [size]="16" />
          </button>
        </div>
      </form>
    </app-wizard-shell>
  `,
})
export class CreateOrg {
  private readonly orgs = inject(OrgService);
  private readonly onboarding = inject(OnboardingService);
  private readonly router = inject(Router);
  protected readonly t = inject(I18nService).t;

  protected readonly name = signal('');
  protected readonly slug = signal('');
  protected readonly slugTouched = signal(false);
  protected readonly slugState = signal<'idle' | 'checking' | 'free' | 'taken'>('idle');
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly complete = computed(
    () => this.name().trim().length > 1 && this.slug().length > 2 && this.slugState() !== 'taken',
  );

  constructor() {
    effect((onCleanup) => {
      const slug = this.slug();
      if (slug.length < 3) {
        this.slugState.set('idle');
        return;
      }

      this.slugState.set('checking');
      const timer = setTimeout(() => {
        void this.orgs
          .slugAvailable(slug)
          .then((available) => this.slugState.set(available ? 'free' : 'taken'))
          .catch(() => this.slugState.set('idle'));
      }, 350);

      onCleanup(() => clearTimeout(timer));
    });
  }

  protected onNameChange(value: string): void {
    this.name.set(value);
    if (!this.slugTouched()) {
      this.slug.set(slugify(value));
    }
  }

  protected onSlugChange(value: string): void {
    this.slugTouched.set(true);
    this.slug.set(slugify(value));
  }

  async submit(event: Event): Promise<void> {
    event.preventDefault();
    if (this.submitting() || !this.complete()) return;

    this.submitting.set(true);
    this.error.set(null);

    try {
      await this.orgs.create(this.name().trim(), this.slug(), 'kanban');
      await this.onboarding.start();
      await this.router.navigate(['/onboarding']);
    } catch (error) {
      this.error.set(this.t(messageKey(error)));
    } finally {
      this.submitting.set(false);
    }
  }
}

function messageKey(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) return 'state.errorTitle';
  if (error.status === 409) return 'onboarding.slugTaken';
  if (error.status === 400) return 'onboarding.orgInvalid';
  return 'state.errorTitle';
}
