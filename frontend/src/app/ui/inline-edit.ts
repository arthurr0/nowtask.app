import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  model,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { I18nService } from '../core/i18n/i18n.service';

@Component({
  selector: 'ui-inline-edit',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (editing()) {
      @if (multiline()) {
        <textarea
          #field
          [rows]="rows()"
          [value]="draft()"
          [attr.placeholder]="placeholder() ? t(placeholder()) : null"
          [attr.aria-label]="t(ariaLabel() || 'ui.inline.edit')"
          class="focus-ring w-full resize-y rounded-[6px] border border-line bg-surface px-1.5 py-1 text-[13px] text-ink outline-none"
          (input)="draft.set($any($event.target).value)"
          (keydown)="onKeydown($event)"
          (blur)="onBlur()"
        ></textarea>
      } @else {
        <input
          #field
          type="text"
          [value]="draft()"
          [attr.placeholder]="placeholder() ? t(placeholder()) : null"
          [attr.aria-label]="t(ariaLabel() || 'ui.inline.edit')"
          class="focus-ring w-full rounded-[6px] border border-line bg-surface px-1.5 py-1 text-[13px] text-ink outline-none"
          [style.min-height]="'calc(var(--row-h) - 14px)'"
          (input)="draft.set($any($event.target).value)"
          (keydown)="onKeydown($event)"
          (blur)="onBlur()"
        />
      }
    } @else {
      <button
        type="button"
        class="w-full rounded-[6px] px-1.5 py-1 text-left text-[13px]"
        [class]="displayClass()"
        [style.min-height]="'calc(var(--row-h) - 14px)'"
        [disabled]="disabled()"
        [attr.aria-label]="t(ariaLabel() || 'ui.inline.edit')"
        (click)="start()"
      >
        {{ value() || t(placeholder()) }}
      </button>
    }
  `,
  styles: `
    :host {
      display: block;
      min-width: 0;
    }
  `,
})
export class InlineEdit {
  protected readonly t = inject(I18nService).t;

  readonly value = model('');
  readonly placeholder = input('');
  readonly ariaLabel = input('');
  readonly textClass = input('');
  readonly multiline = input(false);
  readonly rows = input(3);
  readonly disabled = input(false);
  readonly saveOnBlur = input(true);

  readonly saved = output<string>();
  readonly cancelled = output<void>();

  protected readonly editing = signal(false);
  protected readonly draft = signal('');

  protected readonly displayClass = computed(() => {
    const classes = [this.textClass()];
    if (!this.disabled()) classes.push('hoverable');
    if (!this.value()) classes.push('text-ink-3');
    return classes.filter(Boolean).join(' ');
  });

  private readonly field = viewChild<ElementRef<HTMLInputElement | HTMLTextAreaElement>>('field');

  constructor() {
    effect(() => {
      if (!this.editing()) return;
      const element = this.field()?.nativeElement;
      if (!element) return;
      element.focus();
      element.select();
    });
  }

  start(): void {
    if (this.disabled() || this.editing()) return;
    this.draft.set(this.value());
    this.editing.set(true);
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.cancel();
      return;
    }
    if (event.key === 'Enter' && (!this.multiline() || event.ctrlKey || event.metaKey)) {
      event.preventDefault();
      this.commit();
    }
  }

  protected onBlur(): void {
    if (!this.editing()) return;
    if (this.saveOnBlur()) {
      this.commit();
      return;
    }
    this.cancel();
  }

  protected commit(): void {
    const next = this.draft().trim();
    this.editing.set(false);
    if (next === this.value()) return;
    this.value.set(next);
    this.saved.emit(next);
  }

  protected cancel(): void {
    this.editing.set(false);
    this.draft.set(this.value());
    this.cancelled.emit();
  }
}
