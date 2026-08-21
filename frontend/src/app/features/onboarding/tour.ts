import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  HostListener,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { OnboardingService } from '../../core/onboarding.service';
import { Icon } from '../../ui/icon';

interface TourStep {
  key: string;
  targets: readonly string[];
}

const STEPS: readonly TourStep[] = [
  { key: 'board', targets: ['[data-tour="board"]'] },
  { key: 'card', targets: ['[data-tour="card"]', '[data-tour="board"] section'] },
  { key: 'views', targets: ['[data-tour="views"]'] },
  { key: 'automations', targets: ['[data-tour="automations"]'] },
];

const LOOKUP_ATTEMPTS = 12;
const LOOKUP_INTERVAL = 200;

const SPOTLIGHT_PADDING = 8;
const SPOTLIGHT_MAX_HEIGHT_RATIO = 0.55;
const BUBBLE_WIDTH = 320;
const BUBBLE_GAP = 14;
const VIEWPORT_MARGIN = 16;

function isVisible(element: Element): boolean {
  const rect = element.getBoundingClientRect();
  return rect.width > 0 && rect.height > 0;
}

interface Box {
  top: number;
  left: number;
  width: number;
  height: number;
}

@Component({
  selector: 'app-onboarding-tour',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    @if (open()) {
      <div class="pointer-events-none fixed inset-0 z-50">
        @if (spot(); as box) {
          <div
            class="absolute rounded-[10px] outline-2 outline-accent transition-all duration-200"
            [style.top.px]="box.top"
            [style.left.px]="box.left"
            [style.width.px]="box.width"
            [style.height.px]="box.height"
            style="box-shadow: 0 0 0 9999px rgb(0 0 0 / 0.55)"
          ></div>
        } @else {
          <div class="absolute inset-0 bg-black/55"></div>
        }

        <div
          class="pointer-events-auto absolute flex flex-col gap-3 rounded-card border border-line bg-surface p-4 shadow-lg"
          role="dialog"
          aria-modal="true"
          [style.top.px]="bubble().top"
          [style.left.px]="bubble().left"
          [style.width.px]="bubbleWidth"
        >
          <div class="flex items-center gap-2">
            <span class="font-mono text-[11px] tracking-[0.08em] text-ink-3 uppercase">
              {{ index() + 1 }} / {{ steps.length }}
            </span>
            <span class="flex-1"></span>
            <button
              type="button"
              class="hoverable flex h-6 w-6 items-center justify-center rounded-[6px] text-ink-3"
              [attr.aria-label]="t('tour.skip')"
              (click)="skip()"
            >
              <ui-icon name="x" [size]="14" />
            </button>
          </div>

          <h2 class="text-[15px] font-semibold tracking-[-0.015em]">
            {{ t('tour.' + steps[index()].key + '.title') }}
          </h2>
          <p class="text-[13px] leading-[1.65] text-ink-2">
            {{ t('tour.' + steps[index()].key + '.body') }}
          </p>

          <div class="flex items-center gap-1.5 pt-0.5">
            @for (step of steps; track step.key; let i = $index) {
              <span
                class="h-1 flex-1 rounded-full"
                [class.bg-inv]="i <= index()"
                [class.bg-line]="i > index()"
              ></span>
            }
          </div>

          <div class="flex items-center justify-between pt-1">
            <button type="button" class="text-[13px] text-ink-3" (click)="skip()">
              {{ t('tour.skip') }}
            </button>
            <div class="flex items-center gap-2">
              @if (index() > 0) {
                <button
                  type="button"
                  class="flex h-[34px] items-center rounded-card border border-line px-3 text-[13px] text-ink-2"
                  (click)="back()"
                >
                  {{ t('onboarding.back') }}
                </button>
              }
              <button
                type="button"
                class="flex h-[34px] items-center gap-1.5 rounded-card bg-inv px-4 text-[13px] font-medium text-inv-ink"
                (click)="next()"
              >
                {{ last() ? t('tour.finish') : t('tour.next') }}
                <ui-icon name="chevron-right" [size]="15" />
              </button>
            </div>
          </div>
        </div>
      </div>
    }
  `,
})
export class OnboardingTour {
  private readonly onboarding = inject(OnboardingService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly t = inject(I18nService).t;

  protected readonly steps = STEPS;
  protected readonly bubbleWidth = BUBBLE_WIDTH;

  protected readonly index = signal(0);
  private readonly closed = signal(false);
  private readonly target = signal<Box | null>(null);

  protected readonly open = computed(() => {
    const state = this.onboarding.state();
    if (!state || this.closed()) return false;
    return !state.tourSeen && state.step === 'done';
  });

  protected readonly last = computed(() => this.index() === STEPS.length - 1);

  protected readonly spot = computed<Box | null>(() => {
    const box = this.target();
    if (!box) return null;

    const maxHeight = window.innerHeight * SPOTLIGHT_MAX_HEIGHT_RATIO;

    return {
      top: box.top - SPOTLIGHT_PADDING,
      left: box.left - SPOTLIGHT_PADDING,
      width: box.width + SPOTLIGHT_PADDING * 2,
      height: Math.min(box.height, maxHeight) + SPOTLIGHT_PADDING * 2,
    };
  });

  protected readonly bubble = computed(() => {
    const spot = this.spot();
    const viewport = { width: window.innerWidth, height: window.innerHeight };

    if (!spot) {
      return {
        top: Math.round(viewport.height / 2 - 110),
        left: Math.round(viewport.width / 2 - BUBBLE_WIDTH / 2),
      };
    }

    const estimatedHeight = 220;
    const below = spot.top + spot.height + BUBBLE_GAP;
    const above = spot.top - BUBBLE_GAP - estimatedHeight;
    const fitsBelow = below + estimatedHeight < viewport.height - VIEWPORT_MARGIN;

    const top = fitsBelow
      ? below
      : above > VIEWPORT_MARGIN
        ? above
        : Math.min(
            Math.max(VIEWPORT_MARGIN, spot.top),
            viewport.height - estimatedHeight - VIEWPORT_MARGIN,
          );

    const centred = spot.left + spot.width / 2 - BUBBLE_WIDTH / 2;
    const left = Math.min(
      Math.max(VIEWPORT_MARGIN, centred),
      viewport.width - BUBBLE_WIDTH - VIEWPORT_MARGIN,
    );

    return { top: Math.round(top), left: Math.round(left) };
  });

  constructor() {
    effect(() => {
      if (!this.open()) {
        return;
      }

      const step = this.index();
      this.locate(step);
    });

    const remeasure = () => {
      if (!this.open()) return;
      this.measure(STEPS[this.index()].targets);
    };

    window.addEventListener('resize', remeasure);
    window.addEventListener('scroll', remeasure, true);

    this.destroyRef.onDestroy(() => {
      window.removeEventListener('resize', remeasure);
      window.removeEventListener('scroll', remeasure, true);
    });
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    if (this.open()) {
      void this.skip();
    }
  }

  protected next(): void {
    if (this.last()) {
      void this.skip();
      return;
    }
    this.index.update((value) => value + 1);
  }

  protected back(): void {
    this.index.update((value) => Math.max(0, value - 1));
  }

  protected async skip(): Promise<void> {
    this.closed.set(true);
    this.target.set(null);
    await this.onboarding.markTourSeen();
  }

  private locate(step: number, attempt = 0): void {
    if (!this.open() || this.index() !== step) {
      return;
    }

    const element = this.find(STEPS[step].targets);

    if (element) {
      element.scrollIntoView({ block: 'nearest', inline: 'nearest' });
      requestAnimationFrame(() => this.measure(STEPS[step].targets));
      return;
    }

    this.target.set(null);

    if (attempt < LOOKUP_ATTEMPTS) {
      setTimeout(() => this.locate(step, attempt + 1), LOOKUP_INTERVAL);
    }
  }

  private find(selectors: readonly string[]): Element | null {
    for (const selector of selectors) {
      const element = document.querySelector(selector);
      if (element && isVisible(element)) {
        return element;
      }
    }
    return null;
  }

  private measure(selectors: readonly string[]): void {
    const element = this.find(selectors);

    if (!element) {
      this.target.set(null);
      return;
    }

    const rect = element.getBoundingClientRect();

    this.target.set({
      top: rect.top,
      left: rect.left,
      width: rect.width,
      height: rect.height,
    });
  }
}
