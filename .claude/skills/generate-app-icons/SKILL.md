---
name: generate-app-icons
description: Generate minimalist black-on-transparent app icons as multi-density PNGs using Python+PIL, or as hand-authored vector drawables, for this Android app. Use when asked to create, design, or add new icons for bottom-nav items, mode-selection cards, buttons, or any other UI element in this project.
---

# Generate App Icons

This project targets API 19, so all icons must work without Jetpack Compose.
Two approaches coexist in the codebase — pick the right one per case.

## Decide: PNG (PIL) vs Vector XML

| Use PNG + PIL when... | Use Vector XML when... |
|---|---|
| Icon is complex/organic, or drawing it as `<path>` data would be painful | Icon is a simple shape PIL primitives (rect/ellipse/line/polygon) express cleanly |
| You want a quick raster preview to sanity-check shape/spacing before committing | You want a single file, no density folders, free scaling/tinting |
| Following the existing `ic_nav_*` / `ic_mode_*` precedent | Following the existing `ic_calendar` / `ic_add` / `ic_instruments` precedent |

Default to vector XML for genuinely simple shapes (matches `ic_calendar.xml`,
`ic_add.xml`, `ic_instruments.xml` — see `app/src/main/res/drawable/`). Use
the PIL workflow below when a shape is easier to compose with raster drawing
primitives than with SVG-style path data, or when asked explicitly for
generated PNG icons.

## PNG + PIL workflow

1. Design each icon in a **24x24dp viewport**, matching every existing icon
   size in this project.
2. Draw at a **supersampled canvas** (e.g. 20x → 480x480px) with plain black
   fill/stroke on a transparent background, using `PIL.ImageDraw` primitives
   (`rounded_rectangle`, `line`, `polygon`, `ellipse`). Supersampling +
   `Image.LANCZOS` downscaling is what gives clean anti-aliased edges at
   small sizes — do not draw directly at 24px.
3. Downscale and export one PNG per Android density bucket into
   `app/src/main/res/drawable-<density>/`:

   | density | factor | px (24dp base) |
   |---|---|---|
   | mdpi | 1.0 | 24 |
   | hdpi | 1.5 | 36 |
   | xhdpi | 2.0 | 48 |
   | xxhdpi | 3.0 | 72 |
   | xxxhdpi | 4.0 | 96 |

   Use `scripts/gen_icons.py` as the template — it already encodes this
   table and the export loop. Copy it to the scratchpad, replace the
   `ICONS` dict with new `draw_xxx(draw)` functions, and run it; it writes
   directly into `app/src/main/res/drawable-*/`.
4. **Naming convention**: `ic_<feature>_<name>.png`, e.g. `ic_nav_home.png`
   (bottom nav) or `ic_mode_instrument_income.png` (a mode-selection card
   under `ui/transactions/`). Keep the same base name across all five
   density folders.
5. **Preview before wiring in**: composite the xxxhdpi PNGs onto one sheet
   with a white background, upscale with `Image.NEAREST` (no smoothing —
   you want to see the actual pixels), and `Read` the sheet as an image to
   confirm shapes read clearly at a glance before touching any layout XML.

## Wiring into layouts

Two tinting patterns exist — pick based on where the icon is used:

- **Menu items consumed by a tint-applying widget** (e.g.
  `BottomNavigationView` via `menu/bottom_nav_menu.xml`): reference the PNG
  directly via `android:icon="@drawable/ic_nav_x"` — the widget's
  `itemIconTint`/state list applies color, so the source PNG's own color
  doesn't matter (draw it opaque black).
- **Anywhere else (cards, buttons, plain `ImageView`)**: add an `ImageView`
  with `android:src="@drawable/ic_x"` and
  `android:tint="?attr/colorControlNormal"` so it follows the light/dark
  theme (`values/` vs `values-night/`), matching `ic_calendar.xml`'s own
  `android:tint` usage. See `app/src/main/res/layout/activity_add_transaction.xml`
  mode-selection cards for the pattern.

## After generating

- Run `./gradlew assembleDebug testDebugUnitTest` per project convention —
  a layout/drawable-only change needs no new unit tests, but the build must
  still pass.
- `git status --short` to confirm only the intended density folders and
  layout file changed.
