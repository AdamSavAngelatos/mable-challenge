package com.mable.bank.csv;

import com.mable.bank.domain.TransferStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class TransferCsvReaderTest {

    private final TransferCsvReader reader = new TransferCsvReader();

    @TempDir
    Path tempDir;

    @Test
    void parsesValidRowsIntoTransfers() throws IOException {
        Path file = writeFile("1111234522226789,1212343433335665,500.00\n3212343433335755,2222123433331212,1000.00\n");

        TransferCsvReader.Result result = reader.read(file);

        assertThat(result.invalidRows()).isEmpty();
        assertThat(result.transfers())
                .extracting(t -> t.fromAccountNumber(), t -> t.toAccountNumber(), t -> t.amount().toDecimalString())
                .containsExactly(
                        tuple("1111234522226789", "1212343433335665", "500.00"),
                        tuple("3212343433335755", "2222123433331212", "1000.00")
                );
    }

    @Test
    void malformedAccountNumberIsNotRejectedAtParseTime() throws IOException {
        // A wrong-length account number is left to surface as UNKNOWN_ACCOUNT when the
        // ledger looks it up -- not rejected here, so account validity has one source
        // of truth instead of being checked in two places.
        Path file = writeFile("not-16-digits,1212343433335665,500.00\n");

        TransferCsvReader.Result result = reader.read(file);

        assertThat(result.invalidRows()).isEmpty();
        assertThat(result.transfers()).hasSize(1);
    }

    @Test
    void rejectsRowsWithWrongFieldCount() throws IOException {
        Path file = writeFile("1111234522226789,1212343433335665\n");

        TransferCsvReader.Result result = reader.read(file);

        assertThat(result.transfers()).isEmpty();
        assertThat(result.invalidRows()).hasSize(1);
        assertThat(result.invalidRows().get(0).status()).isEqualTo(TransferStatus.INVALID_ROW);
        assertThat(result.invalidRows().get(0).rawRow()).isEqualTo("1111234522226789,1212343433335665");
    }

    @Test
    void rejectsNonNumericAmount() throws IOException {
        Path file = writeFile("1111234522226789,1212343433335665,lots\n");

        TransferCsvReader.Result result = reader.read(file);

        assertThat(result.transfers()).isEmpty();
        assertThat(result.invalidRows()).hasSize(1);
    }

    @Test
    void rejectsNegativeAmount() throws IOException {
        Path file = writeFile("1111234522226789,1212343433335665,-5.00\n");

        TransferCsvReader.Result result = reader.read(file);

        assertThat(result.transfers()).isEmpty();
        assertThat(result.invalidRows()).hasSize(1);
        assertThat(result.invalidRows().get(0).reason()).contains("must not be negative");
    }

    @Test
    void oneMalformedRowDoesNotFailTheWholeFile() throws IOException {
        Path file = writeFile(
                "1111234522226789,1212343433335665,500.00\n"
                        + "bad-row\n"
                        + "3212343433335755,2222123433331212,1000.00\n"
        );

        TransferCsvReader.Result result = reader.read(file);

        assertThat(result.transfers()).hasSize(2);
        assertThat(result.invalidRows()).hasSize(1);
    }

    private Path writeFile(String content) throws IOException {
        Path file = tempDir.resolve("transactions.csv");
        Files.writeString(file, content);
        return file;
    }
}
