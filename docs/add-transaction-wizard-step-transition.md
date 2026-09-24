# AddTransactionActivity: step 1 ↔ step 2 slide transition

Package: `dev.fitiavana.accounting.ui.transactions`

## What it does

`AddTransactionActivity` shows a two-step wizard: `step_mode_selection`
(step 1) then `step_transaction_form` (step 2). Switching between them used
to be a hard `View.VISIBLE`/`View.GONE` swap. `showStep()`
(`AddTransactionActivity.kt`) now wraps that swap in a
`TransitionManager.beginDelayedTransition` with a pair of `Slide`
transitions, so the entering step slides in from one edge while the leaving
step slides out the other — forward and backward navigation both animate,
with the direction flipped between the two:

```kotlin
private fun showStep(newStep: Step) {
    step = newStep
    val enteringView =
        if (newStep == Step.TRANSACTION_FORM) stepTransactionForm else stepModeSelection
    val leavingView =
        if (newStep == Step.TRANSACTION_FORM) stepModeSelection else stepTransactionForm
    val enteringEdge = if (newStep == Step.TRANSACTION_FORM) Gravity.END else Gravity.START
    val leavingEdge = if (newStep == Step.TRANSACTION_FORM) Gravity.START else Gravity.END

    TransitionManager.beginDelayedTransition(
        rootContainer,
        TransitionSet()
            .setOrdering(TransitionSet.ORDERING_TOGETHER)
            .addTransition(Slide(enteringEdge).addTarget(enteringView))
            .addTransition(Slide(leavingEdge).addTarget(leavingView))
            .setDuration(220)
    )
    stepModeSelection.visibility = ...
    stepTransactionForm.visibility = ...
}
```

## The subtlety: two problems, not one

A `Transition` doesn't instantly hide the outgoing view when you set it to
`GONE`. It keeps it `VISIBLE` for the duration of the animation so it has
something to animate (translating it off-screen), and only applies the real
`GONE` once the animation ends. That means **for the ~220ms of the
transition, both `step_mode_selection` and `step_transaction_form` are
simultaneously `VISIBLE`** in the view hierarchy. That single fact caused
two separate visual bugs that both had to be fixed.

### Problem 1: weighted siblings reflow when both are visible

The original layout (`activity_add_transaction.xml`) had both steps as
siblings directly inside the root `LinearLayout`, each with
`layout_height="0dp"` + `layout_weight="1"`:

```xml
<LinearLayout android:orientation="vertical">
    <Toolbar .../>
    <LinearLayout android:id="@+id/step_mode_selection"
        android:layout_height="0dp" android:layout_weight="1" .../>
    <LinearLayout android:id="@+id/step_transaction_form"
        android:layout_height="0dp" android:layout_weight="1" .../>
</LinearLayout>
```

A weighted `LinearLayout` divides its remaining space across every child
that is currently `VISIBLE`. With one child `VISIBLE` (the normal case) that
child gets the full weight-1 share. But mid-transition, with *both*
children `VISIBLE`, the `LinearLayout` re-measures and splits the space
50/50 between them — each step is squeezed into half the screen height for
that frame, fighting the `Slide` transition's translation math (which is
computed against each view's *un-squeezed* bounds).

Fix: nest both steps inside a `FrameLayout` (full `match_parent` height
each) instead of weighting them directly in the `LinearLayout`. A
`FrameLayout` never redistributes space based on how many children are
`VISIBLE` — each child is always measured at full size and simply stacked
in the same bounds.

```xml
<FrameLayout android:layout_height="0dp" android:layout_weight="1">
    <LinearLayout android:id="@+id/step_mode_selection"
        android:layout_height="match_parent" .../>
    <LinearLayout android:id="@+id/step_transaction_form"
        android:layout_height="match_parent" .../>
</FrameLayout>
```

### Problem 2: neither step has an opaque background

Fixing the reflow wasn't enough on its own — the overlap was still visibly
wrong, just now as a full-screen double-exposure instead of a squeeze.
Neither `step_mode_selection` nor `step_transaction_form` declared an
`android:background`, so both containers are transparent and only ever
relied on the activity window's background showing through. Stacking two
transparent, full-size views in a `FrameLayout` means the *content* of the
bottom one (whichever step isn't animating on top) shows straight through
the top one for the whole transition — literally what it looked like:
"one of the screens is transparent."

Fix: give each step container an explicit opaque background,
`?attr/colorSurface` (the same attribute `fragment_transactions.xml` /
`fragment_accounts.xml` use for their own root containers), so whichever
step is drawn on top during the transition fully occludes the one
underneath:

```xml
<LinearLayout android:id="@+id/step_mode_selection"
    android:background="?attr/colorSurface" .../>
<LinearLayout android:id="@+id/step_transaction_form"
    android:background="?attr/colorSurface" .../>
```

With both fixes in place, z-order in the `FrameLayout` (later children draw
on top of earlier ones — `step_transaction_form` is declared after
`step_mode_selection`, so it's always the top layer) plus an opaque
background on each step gives a clean "one screen slides across the other,
fully covering it" reveal — the expected wizard-step look — instead of
either a reflow squeeze or a see-through double-exposure.

## Notes

- `rootContainer` (`R.id.add_transaction_root`, the outer vertical
  `LinearLayout`) is the transition's scene root passed to
  `beginDelayedTransition`. It has to be an ancestor of both steps that
  Android can safely re-measure/redraw for the transition; the `FrameLayout`
  itself would also work as scene root, but the existing tests and code use
  the already-`findViewById`'d outer container.
- Robolectric assertions on step visibility (`AddTransactionActivityTest`)
  still check `view.visibility` immediately after `performClick()`/back
  navigation. `beginDelayedTransition` schedules the *animation* but the
  target `visibility` values are still applied synchronously in the same
  call, so those assertions are unaffected by this change.
- `androidx.transition` (`Slide`, `TransitionSet`, `TransitionManager`) is
  pulled in transitively via the Material dependency already in
  `build.gradle.kts` — no new dependency was added.
