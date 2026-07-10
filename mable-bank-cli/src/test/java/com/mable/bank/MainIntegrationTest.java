package com.mable.bank;

import com.mable.bank.csv.AccountBalanceCsvReader;
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
                .extracting(a -> a.accountNumber(), a -> a.getBalance().toDecimalString())
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
                .extracting(a -> a.accountNumber(), a -> a.getBalance().toDecimalString())
                .containsExactlyInAnyOrder(
                        tuple("1111234522226789", "4820.50"),
                        tuple("1111234522221234", "14974.40"),
                        tuple("2222123433331212", "1750.00"),
                        tuple("1212343433335665", "1525.60"),
                        tuple("3212343433335755", "43679.50")
                );
    }
}
