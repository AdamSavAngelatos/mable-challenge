package com.mable.bank.csv;

import com.mable.bank.domain.Account;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class AccountBalanceCsvReaderTest {

    private final AccountBalanceCsvReader reader = new AccountBalanceCsvReader();

    @TempDir
    Path tempDir;

    @Test
    void parsesValidRowsIntoAccounts() throws IOException {
        Path file = writeFile("1111234522226789,5000.00\n1212343433335665,1200.00\n");

        AccountBalanceCsvReader.Result result = reader.read(file);

        assertThat(result.rejectedRows()).isEmpty();
        // Account is an entity (identity = accountNumber), not a value object, so it
        // has no value-based equals() -- assert on the fields the reader is responsible for.
        assertThat(result.accounts())
                .extracting(Account::accountNumber, a -> a.getClosingBalance().toDecimalString())
                .containsExactly(
                        tuple("1111234522226789", "5000.00"),
                        tuple("1212343433335665", "1200.00")
                );
    }

    @Test
    void skipsBlankLines() throws IOException {
        Path file = writeFile("1111234522226789,5000.00\n\n1212343433335665,1200.00\n");

        AccountBalanceCsvReader.Result result = reader.read(file);

        assertThat(result.accounts()).hasSize(2);
    }

    @Test
    void rejectsRowsWithWrongFieldCountWithoutFailingTheWholeFile() throws IOException {
        Path file = writeFile("1111234522226789,5000.00,extra\n1212343433335665,1200.00\n");

        AccountBalanceCsvReader.Result result = reader.read(file);

        assertThat(result.accounts()).hasSize(1);
        assertThat(result.rejectedRows()).hasSize(1);
        assertThat(result.rejectedRows().get(0).reason()).contains("expected 2 fields");
    }

    @Test
    void rejectsMalformedAccountNumberOrAmount() throws IOException {
        Path file = writeFile(
                "not-16-digits,5000.00\n"
                        + "1111234522226789,not-a-number\n"
                        + "1212343433335665,1200.00\n"
        );

        AccountBalanceCsvReader.Result result = reader.read(file);

        assertThat(result.accounts()).hasSize(1);
        assertThat(result.rejectedRows()).hasSize(2);
    }

    private Path writeFile(String content) throws IOException {
        Path file = tempDir.resolve("balances.csv");
        Files.writeString(file, content);
        return file;
    }
}
