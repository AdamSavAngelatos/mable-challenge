# Mable Bank CLI

A simple banking system that loads a company's account balances, applies a day's
transfers from a CSV file, and never lets a transfer take an account below $0.

## Running it

```
mvn clean package
java -jar target/mable-bank-cli-1.0.0.jar <balances.csv> <transactions.csv> [outputDir]
```

`outputDir` defaults to the current directory. Two files are written there:

- **`updated-account-balances.csv`** — the closing balances, in the exact same
  schema as the input balances file. This is the real state output: a day's
  closing balances are the next day's starting balances, so this file is meant
  to be fed straight back in as `<balances.csv>` for the following day's run.
- **`result.txt`** — a human-readable audit trail: one line per transfer with
  its outcome, plus a summary. The same content also prints to stdout live.

Example, using the files from this repo:

```
java -jar target/mable-bank-cli-1.0.0.jar \
  ../MableBackEndCodeTest/mable_account_balances.csv \
  ../MableBackEndCodeTest/mable_transactions.csv \
  ./out
```

## Testing

```
mvn test      # run the suite
mvn verify    # run the suite + generate a JaCoCo coverage report at target/site/jacoco/index.html
```

## Design decisions and tradeoffs

This system is deliberately small. Below are the choices that shape it and the
reasoning behind each — including a few I'd happily be challenged on.

**Java, no framework.** No language/framework was specified for this exercise
(the rubric literally says "uses rspec," a leftover from what's presumably a
Rails-based internal template — worth a laugh, not a constraint). I chose Java
because it's my deepest, most current professional fluency, and the rubric is
heavily OO-flavored (domain models, encapsulation, separation of concerns) —
Java plays to that directly.

**CLI, not a REST service.** I considered wrapping this in a small REST API (I
have recent experience there too), but the spec describes a batch process —
"load balances... accept a day's transfers" — with no mention of a client or
network boundary. A CLI matches that literally, and it resolves an otherwise
awkward question of "what does this system output" cleanly: two files, not an
implicit in-memory state nobody can inspect. The domain core
(`Ledger`/`TransferProcessor`) has no dependency on the CLI layer at all, so
wrapping it in a web layer later wouldn't require a redesign.

**No Spring Boot.** Spring Boot's value — a DI container, embedded web server,
auto-configuration, environment profiles — is aimed at long-running services
with many collaborating components. A CLI that reads two files and writes a
report has none of that. `Main` wires up `new Ledger()` and
`new TransferProcessor(ledger)` by hand, which is both sufficient and more
legible than Spring conventions would be at this size.

**`Money` wraps `BigDecimal`, deliberately kept minimal.** `BigDecimal` already
avoids the float-precision problems `double` would introduce, so `Money`'s job
isn't precision — it's enforcing one consistent scale/rounding policy
everywhere (as `private static final` constants, not external config — there's
no multi-currency requirement here, so a config system would solve a problem
that doesn't exist), and giving methods a domain-meaningful type instead of a
bare `BigDecimal`. It only has the operations actually used (`add`,
`subtract`, comparisons, parse/format) — no multiply/divide, no multi-currency,
no locale formatting, to keep it from becoming a speculative general-purpose
currency class.

**The $0 floor is enforced inside `Account.debit()`,** not by callers checking
first. `Ledger.applyTransfer()` still checks `canDebit()` before calling
`debit()`, so the normal business-rejection path never touches exceptions —
but it's structurally impossible to write a code path anywhere in the system
that overdraws an account, because the invariant lives in the model, not in
caller discipline.

**No interfaces for `Ledger` or `TransferProcessor`.** Each has exactly one
implementation and no current need for substitution. An interface with a
single implementation is a common but usually unjustified pattern in
enterprise Java — I only reach for one when there's a genuine second
implementation or a real substitution point, and there isn't one here. Tests
use the real `Ledger` directly (fast, deterministic, in-memory) rather than
mocking through an interface.

**Hand-rolled CSV parsing, not a library.** This is a judgment call about
*this* input specifically, not a blanket position: the format is fully
constrained by the spec (fixed column count, plain numeric/16-digit fields, no
possibility of embedded commas or quotes). A CSV library's real value —
correct quoting/escaping, multiline fields — can't come up in data shaped like
this, so pulling one in would mean paying dependency weight for a problem that
structurally cannot occur. If the input were less constrained, I'd reach for
Apache Commons CSV instead of hand-rolling.

**A malformed account number in a transfer row isn't rejected at parse time.**
It's left to surface naturally as `UNKNOWN_ACCOUNT` when the ledger looks it
up, so "what makes an account number valid" has one source of truth (the
ledger, populated from the balances file) instead of being checked in two
places that could drift apart.

**Transfers within a batch are applied independently, in file order.** An
insufficient-funds or unknown-account row is rejected and recorded, but does
*not* block later, unrelated transfers in the same file — that's the natural
reading of "money cannot be transferred... if it will put the account balance
below $0": a per-transfer rule, not a whole-batch abort.

## Known simplifications (named deliberately, not oversights)

- **No database/persistence layer.** State lives in memory for the duration of
  one run. Durability across runs is handled by the
  `updated-account-balances.csv` output instead — explicit and chainable,
  rather than automatic.
- **Idempotency, precisely scoped.** Because there's no persistence, this tool
  is a pure function of its two inputs: the same `balances.csv` +
  `transactions.csv` always produce the same output, so re-running with the
  *original* files is naturally idempotent — nothing accumulates. The actual
  risk is a chaining/workflow one: if the same `transactions.csv` were
  mistakenly reprocessed against the `updated-account-balances.csv` it already
  produced, that day's transfers would be double-applied. That's a gap in the
  surrounding workflow (e.g. date-stamped filenames, a processed-batch marker
  in a real system), not a defect in this tool's logic.
- **No multi-company/tenant support** — the spec explicitly scopes this to a
  single company.
- **No authentication/authorization.** This tool trusts whatever CSV it's
  pointed at, with no verification of who submitted it or what they're
  authorized to move. There's no network surface here, so it's not the same
  concern a REST service would have, but it's worth naming explicitly — it's
  arguably the most serious gap for a system whose whole premise is a banking
  service. In production, authenticating the source of a transactions file
  (e.g. per-company signing) would sit at the same kind of boundary an
  API gateway would normally enforce for a networked service.
