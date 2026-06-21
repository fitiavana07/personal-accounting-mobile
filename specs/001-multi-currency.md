# Multi-currency support feature

This document describes multi-currency support features, including support
for securities such as stocks, and cryptocurrencies.

## Accounting rule

Every transaction is accounted in the base currency. It means the rule of
accounting "Debit always equals Credit" must be validated in every transaction,
even for accounts with different currency/security.

For example, I exchange 4500 MGA for 1 USD and track in two asset accounts
named "MGA Account" and "USD Account". This means a decrease of 4500 MGA in
MGA Account and an increase of 1 USD in USD Account. This is tracked in a
transaction as a credit of 4500 on MGA Account, but because of the account
rule DEBIT = CREDIT, we must also have a debit of 4500 on USD Account (in
base currency MGA). Therefore, the transaction records it as a debit of 4500 on
USD Account.

## Storing the change in terms of foreign account

Tracking the changes in an account with different currencies than the base
currency enables many possibilities:

- calculating gains/losses on assets sales (stocks, cryptos, etc.)
- calculating gains/losses on currency exchanges

### Example scenario 1

I buy 1 USD using 4500 MGA. My balance of "USD Account" is 4500 MGA, but the
actual account (in some platforms or bank) holds actually 1 USD. When
converting this 1 USD back, I get 4400 MGA in "MGA Account". In a transaction
it would mean a credit of 4400 MGA on "USD Account" leaving 100 MGA on the
account, but we have sold it all so there should be no remainder on the
account. That 100 MGA is recorded as loss to complete the transaction. The rule
will then be satisfied.

|                           | Debit | Credit |
|---------------------------|-------|--------|
| MGA Account               | 4400  |        |
| Loss on currency exchange | 100   |        | 
| USD Account               |       | 4500   |

### Example scenario 2

Consider the journal transactions below.

Transaction 1 is exchanging 292,500,000 MGA for 65,000 USD.

|             | Debit       | Credit      | Debit (USD) | Credit (USD) | Running Balance (MGA) | Running Balance (USD) |
|-------------|-------------|-------------|-------------|--------------|-----------------------|-----------------------|
| USD Account | 292,500,000 |             | 65,000      |              | 292,500,000           | 65,000                |
| MGA Account |             | 292,500,000 |             |              | 0                     |                       |

Transaction 2 is exchanging 65,000 USD for 1 BTC

|             | Debit       | Credit      | Debit (USD) | Credit (USD) | Debit (BTC) | Credit (BTC) | Running Balance (MGA) | Running Balance (USD) | Running Balance (BTC) |
|-------------|-------------|-------------|-------------|--------------|-------------|--------------|-----------------------|-----------------------|-----------------------|
| BTC Account | 292,500,000 |             | 65,000      |              | 1           |              | 292,500,000           | 65,000                | 1                     | 
| USD Account |             | 292,500,000 |             | 65,000       |             |              | 0                     | 0                     |                       |

Transaction 3 is exchanging 0.5 BTC for 32,000 USD, at price 64,000 USD.

|                  | Debit       | Credit      | Debit (USD) | Credit (USD) | Debit (BTC) | Credit (BTC) | Running Balance (MGA) | Running Balance ( USD) | Running Balance (BTC) |
|------------------|-------------|-------------|-------------|--------------|-------------|--------------|-----------------------|------------------------|-----------------------|
| USD Account      | 144,000,000 |             | 32,000      |              |             |              | 144,000,000           | 32,000                 |                       |
| Loss on BTC Sale | 2,250,000   |             | 500         |              |             |              |                       |                        |                       |
| BTC Account      |             | 146,250,000 |             | 32,500       |             | 0.5          | 146,250,000           | 32,500                 | 0.5                   |

## Backward compatibility (no foreign balance yet)

TBD

## Examples (test cases)

- Buy/Sell USDT <-> MGA
- Buy/Sell BTC <-> USDT
- Buy/Sell NVDA <-> USD

## Task

- Show balance in foreign account currency in the balances screen
- Add final currency in account modification and creation
- Add intermediary currencies in account modification and creation