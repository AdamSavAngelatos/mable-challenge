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

- **`Account` has both `startingBalance` (frozen at construction) and `closingBalance`** (mutated by `debit()`/`credit()`), so `Main` reads both off the same objects via one `ledger.listAccounts()` call at the end, instead of snapshotting early.
- **`record` for immutable data, `class` for mutable state/behavior.** `Transfer`, `TransferResult`, `AccountBalanceCsvReader.Result`/`RejectedRow` are records; `Account`, `Ledger`, `TransferProcessor`, `ReportWriter` are classes. `Account` has no `equals()`/`hashCode()` override (it's an entity, not a value object) — tests compare fields explicitly instead.
- **No interfaces for single-implementation classes** (`Ledger`, `TransferProcessor`) — see README for why. Tests use the real implementations directly.
- **A malformed account number in a transfer row isn't validated at parse time** — it surfaces as `UNKNOWN_ACCOUNT` when `Ledger` looks it up (see README for why).
- **`Main.run()` is package-private and takes an injected `PrintStream`** (not `System.out`), so `MainIntegrationTest` can assert on stdout without touching real global state.
- **The report is written straight to stdout via that `PrintStream`, not through a logging framework** (see README for why — SonarLint flags direct `System.out`/`PrintStream` usage, so expect to justify this one). Only one of `MainIntegrationTest`'s four `Main.run(...)` calls asserts on captured stdout; the rest still inject a throwaway `PrintStream` purely to keep `mvn test` quiet.
- **Public classes/methods get standard Javadoc** (summary + `@param`/`@return`/`@throws`), skipped only where it would restate a self-evident signature (e.g. `Account.accountNumber()`, `Money.isNegative()`). Validate with `mvn javadoc:javadoc` — it catches malformed HTML from raw `<`/`>` in prose (use `{@code List<Account>}`) and summary-less tag blocks. **The plugin silently skips regeneration on pure-comment edits** — run `rm -rf target/reports target/site` before `mvn javadoc:javadoc` to force a real check.
- **Every `class` is `final`** (records/enums are implicitly non-extendable already). None have extension points, so `final` makes that compiler-enforced rather than assumed — e.g. an unrestricted `Account` subclass could override `debit()` to skip the $0-floor check. Same reasoning as "no interfaces for single-implementation classes," applied to inheritance instead.

## Testing

JUnit 6 (Jupiter) + AssertJ. Fixture CSVs live in `src/test/resources/fixtures/`. `MainIntegrationTest` calls `Main.run(...)` directly rather than shelling out to the built jar.

**`MainIntegrationTest` deliberately avoids golden-file/snapshot comparison** for the CLI's output. Two reasons: (1) the report format has changed repeatedly during development, and a golden file trains you to reflexively re-approve a new snapshot rather than verify it's still correct; (2) for `updated-account-balances.csv`, re-reading the output through `AccountBalanceCsvReader` and asserting on the parsed accounts proves the file is genuinely valid input — a byte-for-byte comparison would only prove two files match each other, never that either is well-formed. Prefer targeted, semantic assertions over one large whole-output comparison.
