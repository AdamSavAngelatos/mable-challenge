package com.mable.bank.csv;

import com.mable.bank.domain.Account;
import com.mable.bank.domain.Money;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the account balances CSV: one row per line, "accountNumber,balance",
 * no header. Hand-rolled rather than a library — see README for why this fits
 * this specific input shape.
 */
public final class AccountBalanceCsvReader {

    public record Result(List<Account> accounts, List<RejectedRow> rejectedRows) {
    }

    /** A balance row that failed to parse. Formatting for display is the report writer's job, not the reader's. */
    public record RejectedRow(String rawRow, String reason) {
    }

    public Result read(Path path) throws IOException {
        List<Account> accounts = new ArrayList<>();
        List<RejectedRow> rejectedRows = new ArrayList<>();
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank()) continue;
            try {
                accounts.add(parseLine(line));
            } catch (IllegalArgumentException e) {
                rejectedRows.add(new RejectedRow(line, e.getMessage()));
            }
        }
        return new Result(accounts, rejectedRows);
    }

    private Account parseLine(String line) {
        String[] fields = line.split(",", -1);
        if (fields.length != 2) {
            throw new IllegalArgumentException("expected 2 fields (accountNumber,balance), found " + fields.length);
        }
        String accountNumber = fields[0].trim();
        Money balance = Money.fromDecimalString(fields[1].trim());
        return new Account(accountNumber, balance);
    }
}
