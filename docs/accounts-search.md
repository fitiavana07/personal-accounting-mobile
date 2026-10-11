# Accounts search

Name search on the Accounts screen. The account-type tabs stay visible; search
narrows the list **within the selected tab**.

## Behavior
- Search icon in the app bar (next to "add"); tapping expands an AppCompat
  `SearchView` ("Search by name"). Collapsing it clears the query.
- Live filter: case-insensitive "contains" on the account name, query trimmed;
  blank query = no filtering.
- Combines with the other filters: type tab AND "Hide zero-balance accounts"
  AND name. A zero-balance match stays hidden until the toggle is off.
- The query is kept when switching tabs or toggling the checkbox, and is
  restored (field re-expanded) after recreation, e.g. rotation. It resets when
  the user leaves the screen (the fragment is recreated on bottom-nav switch).
- No matches shows `empty_accounts_filtered`. No per-tab counts and no
  cross-tab hint (out of scope).

## Code
- `AccountsViewModel.searchQuery` / `setSearchQuery`; the name filter runs in
  the `accounts` mediator's `update()` before `AccountListItemBuilder.build`,
  so the zero-balance filter still applies after it.
- `AccountsFragment.setupSearch` wires the menu's `SearchView` to the ViewModel
  (listener attached after restoring the query so it isn't echoed back).
- `res/menu/menu_accounts.xml`: `action_search` uses
  `app:actionViewClass="androidx.appcompat.widget.SearchView"` and
  `showAsAction="ifRoom|collapseActionView"` (API 19 safe).

## Tests
- `AccountsViewModelTest`: substring/case/trim, blank query, hide-zero and type
  combinations, query surviving type changes.
- `AccountsFragmentSearchTest` (Robolectric): menu items, query forwarded to the
  ViewModel, query restored, filtered empty message.
