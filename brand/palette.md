# Palette

The colour system nowtask uses, with the rules that decide where each token may be used. The
tokens are defined once in `frontend/src/styles.css` and everything else, Tailwind utilities
included, reads them through `@theme inline`. Use the token names verbatim.

## Themes and accents

The interface has a light and a dark theme, switched by the `data-theme` attribute on `<html>`,
and five accents switched by `data-accent`: `graphite` (the default), `blue`, `clay`, `moss`
and `plum`. Density (`data-density`) and corner radius (`data-radius`) are separate axes. The
preferences are stored in `localStorage` under `nowtask.prefs` and restored by a script in
`index.html` before the first paint.

Every colour is written in OKLCH so that the accents share one lightness and chroma and only
the hue changes. The neutral hue is 265, a cool grey that reads as graphite rather than blue.

## Tokens

| Token | Light | Dark | What it is for |
|---|---|---|---|
| `--c-bg` | `oklch(0.985 0.002 265)` | `oklch(0.163 0.004 265)` | Page background |
| `--c-surface` | `oklch(1 0 0)` | `oklch(0.204 0.004 265)` | Cards, panels, inputs |
| `--c-surface-2` | `oklch(0.966 0.003 265)` | `oklch(0.238 0.005 265)` | Hovered rows, table headers |
| `--c-surface-3` | `oklch(0.938 0.004 265)` | `oklch(0.272 0.005 265)` | Pressed state, nested panels |
| `--c-line` | `oklch(0.906 0.005 265)` | `oklch(0.301 0.006 265)` | Borders and dividers |
| `--c-line-strong` | `oklch(0.842 0.006 265)` | `oklch(0.382 0.007 265)` | Focused borders, drag handles |
| `--c-ink` | `oklch(0.24 0.008 265)` | `oklch(0.945 0.003 265)` | Body text, the mark |
| `--c-ink-2` | `oklch(0.5 0.007 265)` | `oklch(0.702 0.005 265)` | Secondary text |
| `--c-ink-3` | `oklch(0.645 0.006 265)` | `oklch(0.562 0.005 265)` | Placeholders, meta, keys |
| `--c-inv` | `oklch(0.24 0.008 265)` | `oklch(0.945 0.003 265)` | Inverted surfaces: primary buttons, the logo tile |
| `--c-inv-ink` | `oklch(0.985 0.002 265)` | `oklch(0.163 0.004 265)` | Text on an inverted surface |
| `--c-accent` | per accent | per accent | Active navigation, links, selected state |
| `--c-accent-soft` | per accent | per accent | Tinted background behind an accent element |
| `--c-brand` | `oklch(0.46 0.012 265)` | `oklch(0.78 0.008 265)` | The dot in the mark, brand emphasis |
| `--c-brand-strong` | `oklch(0.36 0.014 265)` | `oklch(0.86 0.006 265)` | Hover state of a brand element |
| `--c-brand-soft` | `oklch(0.93 0.004 265)` | `oklch(0.3 0.006 265)` | Tinted brand background |
| `--c-brand-inv` | `oklch(0.78 0.008 265)` | `oklch(0.46 0.012 265)` | The dot on the logo tile |
| `--c-warn` | `oklch(0.58 0.09 42)` | `oklch(0.72 0.08 42)` | Overdue, blocked, destructive actions |
| `--c-warn-soft` | `oklch(0.948 0.022 42)` | `oklch(0.3 0.04 42)` | Tinted warning background |
| `--c-signal` | `oklch(0.68 0.14 42)` | `oklch(0.78 0.12 42)` | Attention without alarm: a due date, a mention |
| `--c-signal-ink` | `oklch(0.52 0.13 42)` | `oklch(0.82 0.1 42)` | Text in the signal colour |
| `--c-done` | `oklch(0.6 0.12 155)` | `oklch(0.72 0.11 155)` | Completed work |
| `--c-done-ink` | `oklch(0.48 0.11 155)` | `oklch(0.78 0.1 155)` | Text in the done colour |
| `--c-scrim` | `oklch(0.24 0.008 265 / 0.32)` | `oklch(0.12 0.004 265 / 0.6)` | The backdrop behind dialogs |

## Accents

| Accent | Light `--c-accent` | Dark `--c-accent` |
|---|---|---|
| `graphite` | `oklch(0.46 0.012 265)` | `oklch(0.78 0.008 265)` |
| `blue` | `oklch(0.58 0.09 252)` | `oklch(0.705 0.08 252)` |
| `clay` | `oklch(0.58 0.09 42)` | `oklch(0.705 0.08 42)` |
| `moss` | `oklch(0.58 0.09 146)` | `oklch(0.705 0.08 146)` |
| `plum` | `oklch(0.58 0.09 318)` | `oklch(0.705 0.08 318)` |

The soft variant of every accent sits at lightness `0.945` on light and `0.29` to `0.3` on dark,
with a fifth of the chroma, so a tinted chip reads as the same hue at a whisper.

## Rules

- Text is always `--c-ink`, `--c-ink-2` or `--c-ink-3` on a surface, or `--c-inv-ink` on an
  inverted surface. Accent, signal, warn and done colours carry shape and fill; when they carry
  text it is the `-ink` variant, never the base.
- A primary button is `--c-inv` with `--c-inv-ink` text. The accent marks the active item, it
  does not paint buttons.
- Status is shown with a tinted background and a dark or light label, not with coloured text
  on white.
- Focus rings use `--c-accent` at 2 px with a 2 px offset in both themes.

## Radius and density

| Token | Value | Use |
|---|---|---|
| `--r-field` | `6px` | Inputs, buttons, chips |
| `--r-card` | `9px` | Task cards, list rows |
| `--r-panel` | `10px` | Dialogs, side panels |
| `--row-h` | `44px` | Table and list rows in the roomy density |
| `--card-pad` | `14px` | Card padding in the roomy density |
| `--nav-h` | `34px` | Side navigation items |

`data-radius` and `data-density` scale these together, so a compact interface with sharp
corners is a preference, not a fork of the stylesheet.

## Typography

IBM Plex Sans for the interface, IBM Plex Mono for task keys, API keys, paths and anything
the reader may need to compare character by character. Both ship with a fallback stack and are
loaded from Google Fonts in `index.html`.

```css
--font-sans: 'IBM Plex Sans', ui-sans-serif, system-ui, sans-serif;
--font-mono: 'IBM Plex Mono', ui-monospace, monospace;
```

The wordmark is IBM Plex Sans Medium, converted to outlines in the logo files.

## Outside the interface

Transactional mail and the webhook embeds for Slack and Discord use the same palette in sRGB:
ink `#1d1f23`, secondary `#55585f`, background `#f9fafb`, surface `#ffffff`, line `#e5e7eb`,
and the dark variants `#0d0e10`, `#ecedef` and `#b5b7bd`. The `theme-color` meta tags and the
web manifest use `#f9fafb` for light and `#0d0e10` for dark.
