package com.mable.bank.csv;

import com.mable.bank.domain.Account;
import com.mable.bank.domain.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class AccountBalanceCsvWriterTest {

    private final AccountBalanceCsvWriter writer = new AccountBalanceCsvWriter();
    private final AccountBalanceCsvReader reader = new AccountBalanceCsvReader();

    @TempDir
    Path tempDir;

    @Test
    void writesAccountsSortedByAccountNumber() throws IOException {
        Path file = tempDir.resolve("out.csv");

        writer.write(file, List.of(
                new Account("3212343433335755", Money.fromDecimalString("50000.00")),
                new Account("1111234522226789", Money.fromDecimalString("5000.00"))
        ));

        assertThat(java.nio.file.Files.readString(file))
                .isEqualTo("1111234522226789,5000.00\n3212343433335755,50000.00\n");
    }

    @Test
    void writtenFileIsValidInputForTheReader() throws IOException {
        Path file = tempDir.resolve("out.csv");
        List<Account> original = List.of(
                new Account("1111234522226789", Money.fromDecimalString("4820.50")),
                new Account("1212343433335665", Money.fromDecimalString("1725.60"))
        );

        writer.write(file, original);
        AccountBalanceCsvReader.Result reRead = reader.read(file);

        assertThat(reRead.rejectedRows()).isEmpty();
        assertThat(reRead.accounts())
                .extracting(Account::accountNumber, a -> a.getClosingBalance().toDecimalString())
                .containsExactlyInAnyOrder(
                        tuple("1111234522226789", "4820.50"),
                        tuple("1212343433335665", "1725.60")
                );
    }
}
