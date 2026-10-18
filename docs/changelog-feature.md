# Changelog and release notes

`CHANGELOG.md` (repo root, [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)) is the single source of
truth. `scripts/release.sh` adds a section to it for each release, and the app shows it on the **Release notes**
screen (overflow menu, next to Backup / Restore / Sync).

## How a release section is written

`scripts/release.sh` runs `scripts/changelog.sh add <previous-tag> HEAD <version> <date>` after the version bump
and before the build, then commits `CHANGELOG.md` together with `app/build.gradle.kts` in the
`chore: bump version to ...` commit. The build comes after, so the APK ships the new section.

`changelog.sh` builds the section from the Conventional Commit subjects of `<previous-tag>..HEAD`:

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

- `ChangelogScriptsTest` (tooling): both scripts, run in a temp git repo.
- `ChangelogParserTest`, `ChangelogRepositoryTest`, `ChangelogViewModelTest`, `ChangelogAdapterTest`,
  `ChangelogActivityTest`, `MainMenuReleaseNotesTest`.
- `BundledChangelogTest` fails if the asset differs from the repo's `CHANGELOG.md`, i.e. if the Gradle copy breaks.
