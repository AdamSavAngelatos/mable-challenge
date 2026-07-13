# Mable Bank CLI

A simple banking system that loads a company's account balances, applies a day's
transfers from a CSV file, and never lets a transfer take an account below $0.

## Requirements

- JDK 21 or newer
- Maven 3.9+

No other runtime dependencies — no Spring Boot, no CSV library. Tests pull in
JUnit Jupiter and AssertJ, both resolved by Maven.

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

This system is deliberately small. Below are the choices that shape it.

**Java:** No language/framework was specified (the rubric says
"uses rspec," assuming a leftover from a Rails-based template). Java is my
deepest current fluency, and the rubric's OO-flavored asks (domain models,
encapsulation, separation of concerns) play to it directly.

**No Spring Boot/Framework**: its value — DI container, embedded web server, auto-config, profiles
— targets long-running services with many collaborating components, and a
CLI that reads two files and writes a report has none of that. `Main` wires
up `new Ledger()` and `new TransferProcessor(ledger)` by hand, simpler and
more legible at this size.

**No logging framework — the report is printed straight to stdout.**
SonarLint flags direct `System.out`/`PrintStream` use by default, on the
assumption that console output is diagnostic logging that belongs behind a
leveled, filterable logger. That assumption doesn't hold here: the report
isn't diagnostic output, it's the program's actual primary deliverable — the
same content also written to `result.txt`. The
[Command Line Interface Guidelines](https://clig.dev/) make this split
explicit: "the primary output for your command should go to stdout,"
while "log messages, errors, and so on should all be sent to stderr."
Routing the report through a logging library would blur that distinction and
add this project's first non-test runtime dependency for no real benefit.

**CLI, not a REST service.** I considered a REST API, but the spec describes a
batch process — "load balances... accept a day's transfers" — with no client
or network boundary mentioned. A CLI matches that literally, and answers
"what does this system output" cleanly: two files, not in-memory state nobody
can inspect. The domain core (`Ledger`/`TransferProcessor`) doesn't depend on
the CLI layer, so a web layer could be added later without a redesign.

**No interfaces for `Ledger` or `TransferProcessor`.** Each has exactly one
implementation and no need for substitution. An interface over a single
implementation is a common but usually unjustified enterprise-Java habit — I
only add one for a genuine second implementation or substitution point. Tests
use the real `Ledger` directly (fast, deterministic, in-memory) rather than
mocking through an interface.

**Hand-rolled CSV parsing, not a library.** A judgment call about _this_
input, not a blanket position: the format is fully constrained by the spec
(fixed column count, plain numeric/16-digit fields, no embedded commas or
quotes). A library's real value — quoting/escaping, multiline fields — can't
come up in data shaped like this, so it'd be dependency weight for a problem
that can't occur. Less-constrained input, I'd reach for Apache Commons CSV
instead.

**A malformed account number in a transfer row isn't rejected at parse time —**
unlike a malformed account number in the _balances_ file, which is rejected
immediately by `Account`'s own constructor as it's parsed. Validating it again
here would be redundant: the ledger only ever contains account numbers that
already passed that check, so a malformed (or simply nonexistent) account
number in a transfer always misses the lookup and comes back as
`UNKNOWN_ACCOUNT` — the rejection is free.

**Transfers within a batch are applied independently, in file order.** A
rejected row (insufficient funds, unknown account) doesn't block later,
unrelated transfers — the natural reading of "money cannot be transferred...
if it will put the account balance below $0" is a per-transfer rule, not a
whole-batch abort.

## Known simplifications (named deliberately, not oversights)

- **No database/persistence layer.** State lives in memory for one run;
  durability across runs comes from the `updated-account-balances.csv` output
  instead — explicit and chainable, rather than automatic.
- **Idempotency, precisely scoped.** This tool is a pure function of its two
  inputs, so re-running the _original_ files is naturally idempotent. The real
  risk is a chaining one: mistakenly reprocessing the same `transactions.csv`
  against the `updated-account-balances.csv` it already produced would
  double-apply that day's transfers. That's a gap in the surrounding workflow
  (date-stamped filenames, a processed-batch marker), not a defect in this
  tool's logic.
- **No multi-company/tenant support** — the spec explicitly scopes this to a
  single company.
- **No authentication/authorization.** This tool trusts whatever CSV it's
  pointed at — no verification of who submitted it or what they're authorized
  to move. There's no network surface, so it's not a REST-service-style
  concern, but it's arguably the most serious gap for something whose whole
  premise is banking. In production, authenticating the source (e.g.
  per-company signing) would sit at the same boundary an API gateway would
  normally enforce.
- **No verbosity/quiet flag.** stdout always gets the full report today,
  which is fine for interactive use but noisy for an unattended/scripted run.
  The [CLI Guidelines](https://clig.dev/) recommend a `-q`/`--quiet` option
  for exactly this — suppressing non-essential output without redirecting
  stderr to `/dev/null` — which would be worth adding if this ran from cron
  rather than a terminal.
