# Accounting

Android accounting app with double-entry bookkeeping. Targets **API 19 (KitKat)** and above.

## Build variants

The project has two build variants with **separate app identities**, meaning both can be installed simultaneously on the same device with completely isolated data (separate SQLite databases, separate SharedPreferences).

| Variant | App ID | Label | Purpose |
|---------|--------|-------|---------|
| `debug` | `dev.fitiavana.accounting.dev` | Accounting (dev) | Development & testing |
| `release` | `dev.fitiavana.accounting` | Accounting | Production |

## Install from the command line

```bash
# Install the dev build (debug variant)
./gradlew installDebug

# Build a release APK
./gradlew assembleRelease
```

The release build requires signing. For a local unsigned release build:

```bash
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release-unsigned.apk
```

## Configure Android Studio run configurations

Android Studio creates a default **app** run configuration that uses the `debug` variant. To add a release configuration:

1. Open **Run > Edit Configurations…**
2. Click **+** and choose **Android App**
3. Name it `app (release)`
4. Set **Module** to `accounting.app`
5. Under **Launch Options**, set **Deploy** to `APK from app bundle` or leave as default
6. Go to the **General** tab and set **Build variant** to `release`
7. Click **OK**

You can now switch between `app` (debug/dev) and `app (release)` from the run configuration dropdown in the toolbar.

To change the active build variant without creating a separate run configuration: open the **Build Variants** panel (**View > Tool Windows > Build Variants**) and select `debug` or `release` for the `:app` module.

## Dev-only features

When running the `debug` variant, the Transactions screen exposes a **⋮ overflow menu** with development actions:

- **Clear all transactions** — permanently deletes every transaction and its entries (with a confirmation dialog)

These menu items are compiled out entirely in the `release` build via `BuildConfig.DEBUG`.
