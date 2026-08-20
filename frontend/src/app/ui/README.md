# The base component layer (`app/ui`)

All components are standalone, `OnPush` and work in both themes and at every density and rounding
setting (tokens from `styles.css`, heights from `var(--row-h)`).

Every text input (`label`, `placeholder`, `title`, `message`, a menu item's `label` and so on) goes
through `I18nService.t`, so what you pass is a **translation key**. If the key is not in the
dictionary, `t()` returns the given text unchanged, so a literal works too.

New keys use the `ui.*` prefix and live in `core/i18n/{pl,en,de}.ts`.

## `menu.ts`: `Menu`, `MenuItem`

A button with a dropdown (CDK Overlay). It closes on an outside click, Escape and Tab, the arrow
keys move the selection, `Enter`/`Space` pick an item.

```html
<ui-menu
  [items]="rowActions"
  triggerIcon="dots"
  ariaLabel="common.more"
  align="end"
  (selected)="onRowAction($event)"
/>
```

```ts
protected readonly rowActions: MenuItem[] = [
  { id: 'open', label: 'nav.overview', icon: 'eye' },
  { id: 'copy', label: 'common.add', icon: 'clip', shortcut: 'C' },
  { id: 'delete', label: 'common.delete', icon: 'trash', danger: true, separatorBefore: true },
];
```

Inputs: `items` (required), `triggerClass`, `triggerHeight`, `triggerIcon`, `triggerLabel`,
`chevron`, `ariaLabel`, `align` (`start` | `end`), `minWidth`, `disabled`.
Outputs: `selected: MenuItem`, `openChange: boolean`. Methods: `open()`, `close()`.
Projected content lands inside the button. A menu item with `checked` renders as
`menuitemcheckbox`; `keepOpen: true` leaves the menu open after a click.

## `dialog.ts`: `Dialog`

A modal window with a focus trap, background scroll lock, closing on Escape and on a backdrop click.

```html
<ui-dialog [(open)]="editorOpen" title="common.newTask" description="task.subtitle" size="md">
  <ui-text-field label="task.title" [(value)]="draftTitle" />

  <div dialogFooter>
    <button type="button" (click)="editorOpen.set(false)">{{ t('ui.confirm.cancel') }}</button>
    <button type="button" (click)="save()">{{ t('common.save') }}</button>
  </div>
</ui-dialog>
```

Inputs: `open` (model, `[(open)]`), `title`, `description`, `size` (`sm` | `md` | `lg`),
`dismissible`, `closeOnBackdrop`, `showFooter`. Output: `closed: void`.
The footer is content projected with the `dialogFooter` attribute; for a window without actions set
`[showFooter]="false"`.

## `confirm.service.ts`: `ConfirmService`

Confirmation of an irreversible operation, returns a `Promise<boolean>`.

```ts
private readonly confirm = inject(ConfirmService);

async removeTask(key: string): Promise<void> {
  if (!(await this.confirm.askDelete(key))) return;
  await this.store.remove(key);
}
```

```ts
const ok = await this.confirm.ask({
  title: 'ui.confirm.title',
  message: 'ui.confirm.message',
  confirmLabel: 'common.save',
  destructive: false,
});
```

`ask(options)`: `title`, `message`, `confirmLabel`, `cancelLabel`, `destructive`, `icon`.
The `destructive` variant draws the button in the `warn` color and starts with focus on "Cancel".
`askDelete(name?)` is a shortcut for deletion.

## `toast.ts` + `toast.service.ts`: `ToastService`, `ToastHost`, `Toast`

Messages about the result of an operation. The host mounts itself on the first message, nothing has
to be added to a screen's template. The queue shows at most three at once.

```ts
private readonly toasts = inject(ToastService);

archive(task: TaskDto): void {
  const previous = { ...task };
  this.store.archive(task.id);
  this.toasts.success('ui.toast.saved', {
    action: { label: 'ui.toast.undo', run: () => this.store.restore(previous) },
  });
}
```

`success(message, options?)`, `error(...)`, `info(...)`, `show({ kind, message, ... })`,
`dismiss(id)`, `clear()`. Options: `description`, `duration` (`0` = does not disappear on its own),
`action: { label, run }`. Errors get the `alert` role, the rest `status`.

## Form fields

All fields work both with `ngModel` (`ControlValueAccessor`) and with signals (`[(value)]` or
`[value]` + `(valueChange)`), have `label`, `hint`, `error`, `required`, `disabled` and a shared
`blurred` output.

### `text-field.ts`: `TextField`

```html
<ui-text-field
  label="login.email"
  placeholder="login.email"
  type="email"
  icon="search"
  [clearable]="true"
  [error]="emailError()"
  [(value)]="email"
  (submitted)="signIn()"
/>
```

Additional inputs: `type`, `icon`, `autocomplete`, `maxLength`, `readOnly`, `clearable`,
`ariaLabel`. Output: `submitted: string` (Enter).

### `textarea-field.ts`: `TextareaField`

```html
<ui-textarea-field label="common.log" [rows]="5" [maxLength]="500" [(ngModel)]="note" name="note" />
```

Additional inputs: `rows`, `maxLength` (turns on the character counter), `readOnly`.

### `select-field.ts`: `SelectField`, `SelectOption`

A native select, for when there are few options and they do not need filtering.

```html
<ui-select-field label="common.status" [options]="statusOptions" [(value)]="status" />
```

```ts
protected readonly statusOptions: SelectOption[] = [
  { value: 'todo', label: 'status.todo' },
  { value: 'doing', label: 'status.doing' },
];
```

### `date-field.ts`: `DateField`

The value is `yyyy-mm-dd` (or `yyyy-mm-ddThh:mm` with `withTime`).

```html
<ui-date-field label="task.due" [min]="today" [(value)]="due" />
```

Additional inputs: `min`, `max`, `withTime`, `clearable`.

### `combo-field.ts`: `ComboField`, `ComboOption`

A search box with a list, for picking a person or a label. The ARIA combobox + listbox pattern,
arrow keys, Enter, Escape.

```html
<ui-combo-field label="common.assignee" [options]="people" [(value)]="assignee" />

<ui-combo-field label="common.label" [options]="labels" [multiple]="true" [(value)]="tags" />
```

```ts
protected readonly people: ComboOption[] = [
  { value: 'ana', label: 'Ana Kowalczyk', hint: 'PM', icon: 'user' },
];
```

The value: `string | null` for a single choice, `string[]` with `[multiple]="true"`.
Additional inputs: `options`, `multiple`, `allowClear`, `searchPlaceholder`, `emptyText`.
Output: `openChange: boolean`.

## `inline-edit.ts`: `InlineEdit`

Text turns into a field on click. Enter saves (with `multiline` it is Ctrl/Cmd + Enter), Escape
cancels, leaving the field saves by default.

```html
<ui-inline-edit
  [(value)]="task.title"
  placeholder="common.newTask"
  textClass="text-[15px] font-medium"
  (saved)="renameTask($event)"
/>
```

Inputs: `value` (model), `placeholder`, `ariaLabel`, `textClass`, `multiline`, `rows`, `disabled`,
`saveOnBlur`. Outputs: `saved: string`, `cancelled: void`. Method: `start()`.

## `empty-state.ts`: `EmptyState`

```html
<ui-empty-state
  icon="board"
  title="ui.empty.title"
  description="ui.empty.description"
  actionLabel="common.newTask"
  (action)="createTask()"
/>
```

Inputs: `icon`, `title`, `description`, `actionLabel`, `actionIcon`. Output: `action: void`.
Projected content lands under the button.

## `field-shell.ts`: `FieldShell`, `nextFieldId()`

The shared field frame: the label, the asterisk for `required`, the error message (`role="alert"`)
or the hint. Used inside the fields, handy when writing new ones.
