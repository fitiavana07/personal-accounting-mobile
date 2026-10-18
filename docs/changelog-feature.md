# Changelog and release notes

`CHANGELOG.md` (repo root, [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)) is the single source of
truth. `scripts/release.sh` adds a section to it for each release, and the app shows it on the **Release notes**
screen (overflow menu, next to Backup / Restore / Sync).

## Previewing and editing Unreleased: `make changelog`

On demand only (no git hook): `make changelog` runs `scripts/changelog.sh commit`. That is `sync`, which adds under
`## [Unreleased]` a bullet for every commit since the last tag that has none yet and prints what it added, followed by
a commit of `CHANGELOG.md` alone (`docs(changelog): update Unreleased entries`, the added bullets in the body; other
staged files stay staged, and nothing is committed when the file did not change). Any hand edit already pending in
`CHANGELOG.md` is committed with it. To improve the wording, edit `CHANGELOG.md` and run `make changelog` again (or
commit it yourself). `changelog.sh sync` alone does the first half without committing.

Each bullet written by `sync` ends with a hidden marker, `<!-- c:3f2a91c -->` (a hash of the commit subject). It is
how `sync` and the release know the commit already has a bullet after you reworded it, so nothing is added twice.
Deleting a bullet does not drop its commit: the next `sync` or release adds it again, since the commit is still
unrecorded. Reword the bullet instead (a commit only stays out of the changelog if its type is `chore`, `docs`,
`test` or `style`). A bullet you write without a marker is kept as it is, but it does not stand for any commit, so if
the commit it describes is user-facing it is listed twice. Markers never reach a release section, so the app does not
see them.

## How a release section is written

`scripts/release.sh` runs `scripts/changelog.sh add <previous-tag> HEAD <version> <date>` after the version bump
and before the build, then commits `CHANGELOG.md` together with `app/build.gradle.kts` in the
`chore: bump version to ...` commit. The build comes after, so the APK ships the new section. At the end it prints
the new entries as plain text (`changelog.sh show <version>`).

`add` moves the bullets under `## [Unreleased]` (with your wording, markers removed) into the new section, adds the
commits of `<previous-tag>..HEAD` that Unreleased does not have, and leaves Unreleased empty. With nothing in
Unreleased it is just the commit list (also what `backfill-changelog.sh` relies on):

| Commit | Category |
|--------|----------|
| `feat` | Added |
| `fix` | Fixed |
| `refactor`, `perf`, any `!` breaking change | Changed |
| `revert` | Removed |

`chore`, `docs`, `test`, `style` and non-conventional subjects are left out; a release with nothing else gets
"Maintenance and internal changes." The `scope` becomes the entry prefix (`- sync: add the Sync screen`). The new
section goes right below `## [Unreleased]` (kept empty) and the compare links at the end of the file are updated
from the `origin` remote. `release.sh` refuses to run if `CHANGELOG.md` is missing.

To improve the wording of a release, edit `CHANGELOG.md` and commit it; the next build ships the edit.

## Seeding (done once)

`scripts/backfill-changelog.sh` wrote the sections of all the existing `v*` tags (one `changelog.sh add` per tag,
dated with the tag date). It refuses to run if `CHANGELOG.md` exists, the tree is dirty, or there is no tag.

## In the app

- `app/build.gradle.kts` copies `CHANGELOG.md` into `build/generated/changelog-assets` (task `copyChangelogAsset`,
  a dependency of every `merge*Assets` task) and adds that folder to the assets, so there is no second copy.
- `features/changelog/`: `ChangelogParser` (preamble, `[Unreleased]` and link references are skipped; one
  `ChangelogRelease` per section with its `ChangelogGroup` categories), `ChangelogRepository` (an unreadable file
  gives an empty list) and `AssetChangelogSource`. `AppContainer.changelogRepository` wires them.
- `ui/changelog/`: `ChangelogActivity` + `ChangelogViewModel` + `ChangelogAdapter`, one card per release, newest
  first. Nothing to show gives a hint text.
- Nothing here is stored in Room or synced.

## Tests

- `ChangelogScriptsTest` (tooling): the scripts (`add`, `sync`, `show`, backfill), run in a temp git repo.
- `ChangelogParserTest`, `ChangelogRepositoryTest`, `ChangelogViewModelTest`, `ChangelogAdapterTest`,
  `ChangelogActivityTest`, `MainMenuReleaseNotesTest`.
- `BundledChangelogTest` fails if the asset differs from the repo's `CHANGELOG.md`, i.e. if the Gradle copy breaks.
