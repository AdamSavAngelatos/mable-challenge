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
    void oneMalformedRowDoesNotFailTheWholeFile() throws IOException {
        Path file = writeFile(
                "1111234522226789,5000.00\n"
                        + "bad-row\n"
                        + "1212343433335665,1200.00\n"
        );

        AccountBalanceCsvReader.Result result = reader.read(file);

        assertThat(result.accounts()).hasSize(2);
        assertThat(result.rejectedRows()).hasSize(1);
    }

    @Test
    void rejectsRowsWithWrongFieldCount() throws IOException {
        // 3 fields, should be 2
        Path file = writeFile("1111234522226789,5000.00,extra\n");

        AccountBalanceCsvReader.Result result = reader.read(file);

        assertThat(result.accounts()).isEmpty();
        assertThat(result.rejectedRows()).hasSize(1);
        assertThat(result.rejectedRows().get(0).reason()).contains("expected 2 fields");
        assertThat(result.rejectedRows().get(0).rawRow()).isEqualTo("1111234522226789,5000.00,extra");
    }

    @Test
    void rejectsAccountNumberWithWrongLength() throws IOException {
        // First account number is 15 digits
        Path file = writeFile("111123452222678,5000.00\n1212343433335665,1200.00\n");

        AccountBalanceCsvReader.Result result = reader.read(file);

        assertThat(result.accounts()).hasSize(1);
        assertThat(result.rejectedRows()).hasSize(1);
        assertThat(result.rejectedRows().get(0).reason()).contains("exactly 16 digits");
    }

    @Test
    void rejectsAccountNumberWithNonDigitCharacters() throws IOException {
        // First account number is 16 characters, but one is a letter, not a digit
        Path file = writeFile("1111234522226A89,5000.00\n1212343433335665,1200.00\n");

        AccountBalanceCsvReader.Result result = reader.read(file);

        assertThat(result.accounts()).hasSize(1);
        assertThat(result.rejectedRows()).hasSize(1);
        assertThat(result.rejectedRows().get(0).reason()).contains("exactly 16 digits");
    }

    @Test
    void rejectsMalformedAmount() throws IOException {
        Path file = writeFile("1111234522226789,not-a-number\n1212343433335665,1200.00\n");

        AccountBalanceCsvReader.Result result = reader.read(file);

        assertThat(result.accounts()).hasSize(1);
        assertThat(result.rejectedRows()).hasSize(1);
        assertThat(result.rejectedRows().get(0).reason()).contains("not a valid decimal amount");
    }

    @Test
    void rejectsBothRowsForARepeatedAccountNumber() throws IOException {
        Path file = writeFile(
                "1111234522226789,5000.00\n"
                        + "1111234522226789,9999.00\n"
                        + "1212343433335665,1200.00\n"
        );

        AccountBalanceCsvReader.Result result = reader.read(file);

        assertThat(result.accounts())
                .extracting(Account::accountNumber)
                .containsExactly("1212343433335665");
        assertThat(result.rejectedRows())
                .extracting(AccountBalanceCsvReader.RejectedRow::rawRow)
                .containsExactlyInAnyOrder("1111234522226789,5000.00", "1111234522226789,9999.00");
        assertThat(result.rejectedRows())
                .allSatisfy(row -> assertThat(row.reason()).contains("duplicate account number: 1111234522226789"));
    }

    @Test
    void rejectsAllThreeRowsWhenAnAccountNumberAppearsThreeTimes() throws IOException {
        Path file = writeFile(
                "1111234522226789,5000.00\n"
                        + "1111234522226789,6000.00\n"
                        + "1111234522226789,7000.00\n"
        );

        AccountBalanceCsvReader.Result result = reader.read(file);

        assertThat(result.accounts()).isEmpty();
        assertThat(result.rejectedRows()).hasSize(3);
        assertThat(result.rejectedRows())
                .allSatisfy(row -> assertThat(row.reason()).contains("duplicate account number: 1111234522226789"));
    }

    private Path writeFile(String content) throws IOException {
        Path file = tempDir.resolve("balances.csv");
        Files.writeString(file, content);
        return file;
    }
}
