# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Java CLI (`com.mable.bank.Main`) that loads a company's account balances from CSV, applies a day's transfers from a second CSV, and never lets a transfer take an account below $0. Built for the Mable Back End Code Challenge (`MableBackEndCodeTest/MABLE_BACK_END_CODE_TEST.md` — the original spec and sample data; treat that folder as read-only reference input, not something to edit). Full design rationale and named tradeoffs live in `README.md` — read that before making architectural changes, since most non-obvious choices here were deliberate and are explained there.

## Commands

```
mvn clean package                         # build the runnable jar (target/mable-bank-cli-1.0.0.jar)
mvn test                                  # run the full test suite
mvn test -Dtest=ClassName                 # run a single test class
mvn test -Dtest=ClassName#methodName      # run a single test method
mvn verify                                # run tests + generate JaCoCo coverage report (target/site/jacoco/index.html)

java -jar target/mable-bank-cli-1.0.0.jar <balances.csv> <transactions.csv> [outputDir]
```

No Spring Boot, no application framework — nothing besides the JDK, JUnit 6 (Jupiter), and AssertJ. `Main.run()` wires up every object by hand; there is no DI container to configure.

## Architecture

Four packages under `com.mable.bank`, each with a single, narrow job. `Main.run()` is the only place that wires them together:

- **`domain`** — `Money` (immutable, wraps `BigDecimal`, fixed scale/rounding via `private static final` constants), `Account` (mutable entity with *two* balance fields — see below), `Transfer`/`TransferResult`/`TransferStatus` (immutable records + enum describing one transfer and its outcome).
- **`service`** — `Ledger` (in-memory `Map<String, Account>`; the only place that mutates balances) and `TransferProcessor` (applies a `List<Transfer>` to a `Ledger` in file order, independently — a rejected transfer never blocks later unrelated ones).
- **`csv`** — `AccountBalanceCsvReader`/`Writer` and `TransferCsvReader`. Hand-rolled parsing (no library — see README for why). Readers never format anything for display; malformed rows come back as structured data (`AccountBalanceCsvReader.RejectedRow`, `TransferResult.invalidRow(...)`) for the report layer to render later.
- **`report`** — `ReportWriter`, a pure formatter: every method takes data and returns a `String`, it never does I/O itself. `Main` composes `buildLoadSummary()` + `buildBalancesSection()` + `buildTransferReport()` into one combined report string and writes/prints that *once* to each destination (stdout and `result.txt`). This is deliberate: two independently-written I/O paths for the same content drifted apart once already (see git history / README) — composing one string and writing it twice is how that's structurally prevented from recurring.

### Other established conventions

- **`Account` has both `startingBalance` (frozen at construction) and `closingBalance` (mutated by `debit()`/`credit()`)**, so `Main` can call `ledger.listAccounts()` once, at the end, and read both off the same objects instead of capturing a snapshot early — see `Account`'s javadoc.
- **`record` for immutable data, `class` for anything with mutable state or behavior.** `Transfer`, `TransferResult`, `AccountBalanceCsvReader.Result`/`RejectedRow` are records. `Account`, `Ledger`, `TransferProcessor`, `ReportWriter` are classes. `Account` deliberately has no `equals()`/`hashCode()` override (it's an entity, not a value object) — tests compare its fields explicitly (`extracting(Account::accountNumber, a -> a.getClosingBalance().toDecimalString())`) rather than relying on object equality.
- **No interfaces for single-implementation classes** (`Ledger`, `TransferProcessor`). Tests use the real implementations directly rather than mocking through an interface that has no second implementation to justify it.
- **A malformed account number in a transfer row is not validated at CSV-parse time.** It's left to surface as `UNKNOWN_ACCOUNT` when `Ledger` looks it up, so "what makes an account number valid" has one source of truth instead of two checks that could drift.
- **`Main.run()`** is package-private and takes an injected `PrintStream` (not a hardcoded `System.out`), specifically so `MainIntegrationTest` can capture and assert on stdout without touching real global state.
- **Public classes and methods get industry-standard Javadoc (summary sentence + `@param`/`@return`/`@throws`), except where it would be pure restatement of a self-evident signature** (e.g. a one-line getter that just returns a field with no invariant worth calling out — see `Account.accountNumber()`/`credit()`, or `Money.add()`/`isNegative()`, which have none). Document real, non-obvious facts (an invariant, a formatting/ordering guarantee, an exception contract); don't manufacture a sentence just to have one. Validate with `mvn javadoc:javadoc` after any doc pass — it catches malformed HTML from raw `<`/`>` in prose (use `{@code List<Account>}`, not `List<Account>`) and tag blocks missing a leading summary, which compilation won't catch.

## Testing

JUnit 6 (Jupiter) + AssertJ. Fixture CSVs live in `src/test/resources/fixtures/` (copies of the provided sample files, plus custom fixtures for insufficient-funds/unknown-account and a second day's transactions for testing that a produced `updated-account-balances.csv` is valid as the next run's input). `MainIntegrationTest` calls `Main.run(...)` directly rather than shelling out to the built jar.
