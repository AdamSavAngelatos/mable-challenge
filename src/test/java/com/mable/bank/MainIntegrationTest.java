package com.mable.bank;

import com.mable.bank.csv.AccountBalanceCsvReader;
import com.mable.bank.domain.Account;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class MainIntegrationTest {

    // TODO: edge cases identified but not yet covered by a test here:
    // - an empty transactions file (zero transfers) -- a company with no activity for the
    //   day should still produce a clean report and pass-through closing balances.
    // - a self-transfer (fromAccountNumber == toAccountNumber) -- currently succeeds as a
    //   net no-op because Ledger.applyTransfer() debits then credits the same Account
    //   object, but that's an emergent property of the lookup, not a deliberate check.
    // - a zero-amount transfer -- Transfer's constructor rejects negative amounts but not
    //   zero, so a $0.00 transfer is currently accepted and always trivially succeeds.

    private static final Path FIXTURES = Paths.get("src/test/resources/fixtures");

    @TempDir
    Path outputDir;

    @Test
    void processesTheProvidedSampleFilesAndProducesTheHandTracedBalances() throws IOException {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        Main.run(FIXTURES.resolve("account-balances.csv"), FIXTURES.resolve("transactions.csv"), outputDir,
                new PrintStream(stdout, true, StandardCharsets.UTF_8));

        AccountBalanceCsvReader.Result closing =
                new AccountBalanceCsvReader().read(outputDir.resolve("updated-account-balances.csv"));

        assertThat(closing.rejectedRows()).isEmpty();
        assertThat(closing.accounts())
                .extracting(a -> a.accountNumber(), a -> a.getClosingBalance().toDecimalString())
                .containsExactlyInAnyOrder(
                        tuple("1111234522226789", "4820.50"),
                        tuple("1111234522221234", "9974.40"),
                        tuple("2222123433331212", "1550.00"),
                        tuple("1212343433335665", "1725.60"),
                        tuple("3212343433335755", "48679.50")
                );

        String report = Files.readString(outputDir.resolve("result.txt"));
        assertThat(report).contains("Processed 4 transfers: 4 succeeded, 0 failed.");
        assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("Loaded 5 accounts");

        // Starting balances must reflect the true original values, not the post-transfer
        // ones -- guarded by Account.getStartingBalance() being frozen at construction,
        // independent of when listAccounts() happens to be called.
        assertThat(report).contains(
                "Starting balances:" + System.lineSeparator()
                        + "  1111234522221234: 10000.00" + System.lineSeparator()
                        + "  1111234522226789: 5000.00" + System.lineSeparator()
                        + "  1212343433335665: 1200.00" + System.lineSeparator()
                        + "  2222123433331212: 550.00" + System.lineSeparator()
                        + "  3212343433335755: 50000.00" + System.lineSeparator()
        );
    }

    @Test
    void insufficientFundsAndUnknownAccountsAreRejectedButDoNotBlockOtherTransfers() throws IOException {
        Main.run(FIXTURES.resolve("account-balances.csv"), FIXTURES.resolve("transactions-insufficient-funds.csv"),
                outputDir, new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

        String report = Files.readString(outputDir.resolve("result.txt"));

        assertThat(report).contains("INSUFFICIENT_FUNDS");
        assertThat(report).contains("UNKNOWN_ACCOUNT");
        // Only the one unrelated transfer in the batch (6789 -> 5665, 500.00) should have
        // succeeded -- proving a rejection doesn't block the rest of the batch.
        assertThat(report).contains("Processed 4 transfers: 1 succeeded, 3 failed.");

        // A rejection must leave both accounts it touched completely untouched, not just
        // reported as rejected -- prove it against the actual written balances, not just
        // the report text.
        AccountBalanceCsvReader.Result closing =
                new AccountBalanceCsvReader().read(outputDir.resolve("updated-account-balances.csv"));
        assertThat(closing.accounts())
                .extracting(Account::accountNumber, a -> a.getClosingBalance().toDecimalString())
                .containsExactlyInAnyOrder(
                        tuple("1111234522226789", "4500.00"),
                        tuple("1111234522221234", "10000.00"),
                        tuple("2222123433331212", "550.00"),
                        tuple("1212343433335665", "1700.00"),
                        tuple("3212343433335755", "50000.00")
                );
    }

    @Test
    void malformedRowsInBothInputFilesAreRejectedAndReportedEndToEnd() throws IOException {
        Main.run(FIXTURES.resolve("account-balances-with-malformed-row.csv"),
                FIXTURES.resolve("transactions-with-malformed-row.csv"), outputDir,
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

        String report = Files.readString(outputDir.resolve("result.txt"));

        // The malformed balance row is reported and never makes it into the ledger.
        assertThat(report).contains("Loaded 2 accounts (1 row(s) rejected)");
        assertThat(report).contains(
                "[REJECTED] not-16-digits,1200.00 -> accountNumber must be exactly 16 digits: not-16-digits");

        // The malformed transaction row is reported without blocking the other transfers.
        assertThat(report).contains("[INVALID_ROW] bad-row -> expected 3 fields (from,to,amount), found 1");
        assertThat(report).contains("Processed 3 transfers: 2 succeeded, 1 failed.");

        // The malformed account never appears in the closing balances file, only the
        // two accounts that parsed successfully do.
        AccountBalanceCsvReader.Result closing =
                new AccountBalanceCsvReader().read(outputDir.resolve("updated-account-balances.csv"));
        assertThat(closing.accounts())
                .extracting(Account::accountNumber)
                .containsExactlyInAnyOrder("1111234522226789", "1212343433335665");
    }

    @Test
    void producedBalancesFileIsValidInputForANextRun() throws IOException {
        Path day1Output = outputDir.resolve("day1");
        Main.run(FIXTURES.resolve("account-balances.csv"), FIXTURES.resolve("transactions.csv"), day1Output,
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

        Path day2Output = outputDir.resolve("day2");
        Main.run(day1Output.resolve("updated-account-balances.csv"), FIXTURES.resolve("transactions-day-2.csv"),
                day2Output, new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

        AccountBalanceCsvReader.Result day2Closing =
                new AccountBalanceCsvReader().read(day2Output.resolve("updated-account-balances.csv"));

        assertThat(day2Closing.rejectedRows()).isEmpty();
        assertThat(day2Closing.accounts())
                .extracting(Account::accountNumber, a -> a.getClosingBalance().toDecimalString())
                .containsExactlyInAnyOrder(
                        tuple("1111234522226789", "4820.50"),
                        tuple("1111234522221234", "14974.40"),
                        tuple("2222123433331212", "1750.00"),
                        tuple("1212343433335665", "1525.60"),
                        tuple("3212343433335755", "43679.50")
                );
    }
}
