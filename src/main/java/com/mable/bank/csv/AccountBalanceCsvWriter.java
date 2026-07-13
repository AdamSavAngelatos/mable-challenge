package com.mable.bank.csv;

import com.mable.bank.domain.Account;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Writes accounts back out in the exact same schema {@link AccountBalanceCsvReader}
 * reads — so the output of one run is valid input to the next (day-N's closing
 * balance is day-N+1's starting balance). Always writes closingBalance.
 */
public final class AccountBalanceCsvWriter {

    public void write(Path path, List<Account> accounts) throws IOException {
        String content = accounts.stream()
                .sorted(Comparator.comparing(Account::accountNumber))
                .map(account -> account.accountNumber() + "," + account.getClosingBalance().toDecimalString())
                .collect(Collectors.joining(System.lineSeparator(), "", System.lineSeparator()));
        Files.writeString(path, content);
    }
}
