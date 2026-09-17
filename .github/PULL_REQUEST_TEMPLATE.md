## What this changes

Describe the change and why it is needed. Link the issue it closes, for example `Closes #12`.

## How it was tested

Commands you ran and anything that still needs a real environment to confirm. Say so plainly
if a path could not be tested.

## Checklist

- [ ] The checks for every part I touched pass: `./gradlew build`, `pnpm exec prettier --check .` and `ng build`, `npm run typecheck` and `npm run build`
- [ ] No comments were added to any file
- [ ] Everything is in English, and every new interface string exists in `pl`, `en` and `de`
- [ ] Documentation under `docs/` is updated for any user visible change
- [ ] `CHANGELOG.md` has an entry for anything a user would notice
- [ ] Commit messages follow the conventional commit style in [CONTRIBUTING.md](../CONTRIBUTING.md)
- [ ] Screenshots are attached if the interface changed
- [ ] No breaking change to the API contract, the configuration or the database schema, or it is described here
