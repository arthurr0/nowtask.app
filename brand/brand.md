# Brand

The name, the voice and the marks, and the rules for using them.

## The name

nowtask is about the present tense of work: not the roadmap, not the backlog, the thing the
team is doing now. The name is always written **nowtask**, lowercase, one word, in prose and
in the interface alike. Never Nowtask, NowTask or Now Task. The image names, the Gradle group,
the environment variables and the MCP tool prefix use the same lowercase word.

## Tagline

Primary: **Now, not someday.**

The tagline is a promise about focus. The board shows what is in flight, the rules move the
rest out of the way, and nothing waits for a status meeting.

## Voice

Plain, direct, colleague to colleague. The reader is in the middle of a working day and needs
the fact, not enthusiasm.

- State what happened, then what to do about it.
- No exclamation marks in the interface or the documentation.
- No superlatives, no "blazing fast", no "simply", no "just".
- Prefer the concrete noun: "the rule ran twice", not "something went wrong".
- Error messages name the thing that failed and the next step.
- English everywhere in the code and the documentation. The interface speaks Polish, English
  and German through the dictionaries in `frontend/src/app/core/i18n`, and every string is
  written in all three.

Good: `The invitation has expired. Ask the person who invited you to send a new one.`

Not good: `Oops! This link does not work anymore.`

## The mark

Three rounded bars rising from left to right on a 32 unit grid, with a dot above the middle
bar. The bars are the work in flight, growing as it moves; the dot is the person, or the
moment, that the board is arranged around. Nothing rotates, nothing is shaded: flat fills only,
no gradients, no shadows, no filters.

The geometry is fixed and should be copied, not redrawn. Bars `5` wide with `rx=2.5`, at
`x=6 y=16 h=10` (38 percent opacity), `x=13.5 y=13 h=13` and `x=21 y=6 h=20`. The dot is a
circle at `16, 7.6` with radius `3.1`. The faded first bar is the one asymmetry in the mark and
it is what gives it direction.

| File | Use |
|---|---|
| `mark.svg` | The mark alone, for light backgrounds |
| `logo-horizontal.svg` | Mark and wordmark, for light backgrounds |
| `logo-horizontal-inverse.svg` | Mark and wordmark, for dark backgrounds |
| `favicon.svg` | The inverse mark on an ink tile with rounded corners, for browser tabs and app icons |
| `og-image.png` | 1200 by 630 card for link previews |

The interface never loads these files for the mark itself. The `ui-logo` component in
`frontend/src/app/ui/logo.ts` draws the same geometry from the theme variables, so the mark
follows the theme and the chosen accent without a second file. The PNG icons that the web
manifest references live in `frontend/public/brand`.

On light backgrounds the bars are `#1d1f23` and the dot is `#55585f`. On dark backgrounds the
bars are `#ecedef` and the dot is `#b5b7bd`. In the interface the bars take `--c-ink` and the
dot takes `--c-brand`, or `--c-inv-ink` and `--c-brand-inv` on the tile.

## The wordmark

The wordmark in the horizontal logos is the word nowtask set in IBM Plex Sans Medium and
converted to outlines, so the files render the same everywhere and carry no font. Keep the
outlines when you edit; do not retype the word in a different face.

## Using the marks

- Clear space around the logo is at least a quarter of the mark's height on every side, and
  nothing else may enter it.
- Minimum size: 20 px tall for the mark with the wordmark, 16 px for the mark alone. Below
  20 px drop the wordmark rather than shrinking it.
- Use `favicon.svg` wherever the icon sits on an unknown background or inside a rounded app
  tile. Use `mark.svg` when the background is known to be light.
- Do not recolour the mark outside the palette, do not add gradients, shadows or outlines, do
  not rotate it, do not stretch it, and do not separate the dot from the bars.
- The SVGs are hand written and carry no comments, no embedded fonts and no external
  references. Keep them that way when you edit them.

## Screenshots

Interface screenshots use the light theme and the graphite accent by default, and the dark
theme when the subject is the theme itself. Never show real people, real organizations or real
keys: use the sample names from the documentation and truncate every key to a short prefix
such as `nt_9f2c...`.

## Colour

See `palette.md` for the tokens, the accents and the rules that follow from them.
