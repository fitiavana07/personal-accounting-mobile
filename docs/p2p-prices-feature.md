# Binance P2P prices (home)

Block under the home shortcuts row: top 3 **Buy** ad prices (left) and top 3 **Sell** ad prices (right) for
USDT/MGA on Binance P2P.

## Flow

`HomeFragment` → `HomeViewModel.refreshP2pPrices()` → `P2pPriceRepository.fetch()` → `HttpP2pPriceFetcher`
(`POST https://p2p.binance.com/bapi/c2c/v2/friendly/c2c/adv/search`, request/response handled by `BinanceP2pApi`)
→ `p2pPrices` LiveData → `HomeP2pPricesAdapter`.

- Buy = `tradeType: BUY` (ads you buy USDT from, cheapest first); Sell = `tradeType: SELL` (ads you sell USDT to,
  best price first). Ranking is Binance's; we keep its order.
- Both sides are fetched in parallel; a failing side is `null` and shows "–" while the other still renders.
- Fetched on every home refresh (pull-to-refresh and screen load), on its own thread so it never delays exchange rates.
- Prices are not persisted. Fiat/asset/row count are `HttpP2pPriceFetcher` constructor defaults (`MGA`, `USDT`, 3).
- The endpoint is unofficial; if Binance changes it, the block shows "–".

## Ad line

One line per ad: price (bold), advertiser name, then `min–max` (compact MGA limits) at the end of the line. The
advertiser name is cut to 6 characters + `...` and is the only part that is ellipsized if space runs out; the limits
are never truncated. The max is `dynamicMaxSingleTransAmount`
(configured max capped by the ad's remaining stock — what Binance's app shows), falling back to
`maxSingleTransAmount`.

## Payment method filter

- The header's "All methods ▾" label opens a single-choice dialog ("All methods" + the methods Binance offers).
  One filter applies to both columns; it is sent as `payTypes: [identifier]`, so Binance filters before ranking.
- Methods come from `POST .../public/c2c/adv/filter-conditions` (`{"fiat":"MGA"}`) — never hardcoded. They are cached
  in SharedPreferences (`p2p_prices`) along with the selected identifier (`P2pFilterStore`), and fetched only when
  the cache is empty.
- If a filtered fetch returns no ads on **both** sides, the identifier may have been retired: the method list is
  refreshed and cached; if the selection is no longer in it, the filter is cleared and prices are refetched
  unfiltered. If it is still listed (or the refresh fails) the selection is kept and the block shows "–".
- If the method list can't be loaded (offline, nothing cached), the dialog is replaced by a toast.
