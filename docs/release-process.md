# Release process

Releases are cut from annotated tags on `master`. Everything else is automated: the release
workflow builds and pushes the three container images and publishes a GitHub release with the
changelog section as its notes. This page is for whoever presses the button.

## 1. Before the tag

Make sure `master` is green and the tree is what you want to ship.

```bash
git switch master
git pull
cd backend && ./gradlew build && cd ..
cd frontend && pnpm exec prettier --check . && pnpm exec ng build --configuration production && cd ..
cd mcp && npm run typecheck && npm run build && cd ..
```

Then:

1. Decide the version. Semantic versioning: a breaking change to the API contract, the
   configuration or the database schema is a major bump, new behaviour is a minor bump, fixes
   are a patch bump. While the project is on `0.x` a breaking change bumps the minor.
2. Set the version in three places: `version` in `backend/build.gradle.kts`,
   `nowtask.version` in `backend/app/src/main/resources/application.yml`, and `version` in
   `frontend/package.json` and `mcp/package.json`. `GET /api/meta` reports the backend one.
3. Update `CHANGELOG.md`. Move everything under the unreleased heading into a new
   `## [X.Y.Z] - YYYY-MM-DD` section, keeping the Added, Changed, Fixed, Removed and Security
   groups. Write it for someone upgrading, not for someone reading the diff. The release
   workflow refuses to run without a section for the version.
4. Check that the documentation matches the release, in particular the image tags in
   [install.md](install.md) and the supported versions in `SECURITY.md`.
5. Commit the bump on its own:

```bash
git add CHANGELOG.md backend/build.gradle.kts backend/app/src/main/resources/application.yml frontend/package.json mcp/package.json
git commit -m "chore(release): 0.1.0"
git push
```

Wait for CI on that commit to pass before tagging.

## 2. Cut the tag

```bash
git tag -a v0.1.0 -m "v0.1.0"
git push origin v0.1.0
```

The tag must start with `v` and the rest must be a semantic version, because the workflow
matches on `v*`, the image tags are derived from it, and the changelog lookup strips the `v`.

A tag with a hyphen in it, `v0.2.0-rc1` for example, is treated as a prerelease: the GitHub
release is marked as such and the images do not get the `latest` tag. Use that for anything
you want people to try without recommending it.

## 3. What the workflow produces

`.github/workflows/release.yml` runs three image jobs in parallel and a release job after
them.

**`backend`, `frontend`, `mcp`** each call `docker-publish.yml`, which builds the part's
Dockerfile for `linux/amd64` and `linux/arm64` with QEMU and buildx and pushes to the GitHub
Container Registry as `ghcr.io/arthurr0/nowtask-backend`, `ghcr.io/arthurr0/nowtask-frontend`
and `ghcr.io/arthurr0/nowtask-mcp`. The tags published for `v0.1.0` are `0.1.0`, `0.1` and
`latest`. A prerelease tag gets the version tag only.

The same workflow runs on every push to `master`, where it publishes `edge` and the short
commit hash, and on every pull request, where it only builds for `linux/amd64` to prove the
Dockerfile still works.

**`release`** reads the `## [0.1.0]` section out of `CHANGELOG.md`, appends the list of
images and the compose command, and publishes the GitHub release at
<https://github.com/arthurr0/nowtask.app/releases> with GitHub's generated list of merged
pull requests underneath.

All jobs use the repository `GITHUB_TOKEN`. No extra secret has to be configured: the image
jobs need `packages: write` and the release job needs `contents: write`, and both are declared
in the workflow.

## 4. After the tag

Check the workflow run, then verify the images yourself:

```bash
docker pull ghcr.io/arthurr0/nowtask-backend:0.1.0
docker buildx imagetools inspect ghcr.io/arthurr0/nowtask-backend:0.1.0
```

`imagetools inspect` should list an `amd64` and an `arm64` manifest for each of the three
images.

The first push to `ghcr.io` creates each package as private. Make the three packages public
once, under the package settings on GitHub, or nobody can pull them without authenticating.

Finally, walk the install page on a clean host with the published images, not with your
working tree:

```bash
cp .env.example .env
IMAGE_TAG=0.1.0 docker compose -f docker-compose.prod.yml up -d
curl -fsS http://localhost:8080/api/meta
```

The `version` in the answer has to match the tag.

## 5. Testing the images without releasing

The compose file in the repository root builds all three images from the working tree:

```bash
docker compose build
docker compose up -d
```

To test the multi-architecture build the workflow performs, run buildx locally for one part:

```bash
docker buildx build --platform linux/amd64,linux/arm64 -f backend/Dockerfile backend
```

## 6. If a release goes wrong

Do not move a published tag. Fix the problem on `master` and cut the next patch version.

If a release has to disappear, delete the GitHub release and the tag, delete the image tags in
the package settings, and say why in the changelog of the release that replaces it. Anyone who
already pulled keeps what they pulled, so treat a published tag as permanent and only remove
one when it is actively harmful.
