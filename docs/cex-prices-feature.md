# CEX Prices feature

Shows the spot price of a crypto pair (base/quote) on 7 exchanges: Binance, Bybit,
Bitget, OKX, KuCoin, MEXC, Kraken.

## Flow

`HomeFragment` → `HomeShortcutsAdapter` (button row between the metrics and emergency fund
blocks) → `CexPricesActivity` → `CexPricesViewModel` → `CexPriceRepository` → one
`CexPriceFetcher` per exchange.

- Spinners are fed by `InstrumentRepository.getCryptocurrencies()` (`InstrumentDao.getByType`).
- The ViewModel fetches as soon as base and quote are both chosen and differ. Stale results
  (selection changed mid-flight) are dropped via a request id. The toolbar "Refresh" re-fetches.
- `CexPriceRepository.fetchAll` queries all exchanges in parallel. A failure or an unlisted pair
  gives `CexPrice(price = null, error = …)`, shown as "N/A"; other exchanges are unaffected.
- Prices are not persisted. The last valid pair is remembered in SharedPreferences (`cex_prices`) via
  `CexPairStore`, reached through `CexPriceRepository.getLastPair/saveLastPair`; the ViewModel exposes
  it as `preferredBase/preferredQuote` to pre-select the spinners. No Room table, migration or backup change.

## Adding an exchange

1. Add a `CexId` entry.
2. Add a parser function in `CexPriceParser` (+ test with a sample JSON body).
3. Add a `CexEndpoint` (URL builder + parser) in `CexEndpoints.all` (+ URL test).

The pair symbol is built from the instrument `code`s, so codes must match exchange tickers.
Kraken maps `BTC` → `XBT`; other Kraken-specific asset names are not mapped.

## API 19 caveat

HTTP goes through `Api19HttpClients` (TLS 1.2). Devices with outdated root certificates may fail
to reach some exchanges; those show "N/A".
