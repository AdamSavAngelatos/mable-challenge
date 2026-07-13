package com.mable.bank.csv;

import com.mable.bank.domain.Money;
import com.mable.bank.domain.Transfer;
import com.mable.bank.domain.TransferResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a day's transactions CSV: one row per line, "fromAccountNumber,toAccountNumber,amount",
 * no header. A malformed account number (wrong digit count) is deliberately NOT rejected
 * here — it's left to surface naturally as UNKNOWN_ACCOUNT when the ledger looks it up,
 * so account-validity has one source of truth (the ledger) instead of duplicating the
 * 16-digit check in two places.
 */
public final class TransferCsvReader {

    /**
     * The outcome of reading a transactions CSV.
     *
     * @param transfers   the transfers successfully parsed, in file order
     * @param invalidRows any rows that could not be parsed, each as a
     *                    {@link com.mable.bank.domain.TransferStatus#INVALID_ROW} result
     */
    public record Result(List<Transfer> transfers, List<TransferResult> invalidRows) {
    }

    /**
     * Reads and parses the transactions CSV at {@code path}, one row per line. A row
     * that fails to parse is recorded in {@link Result#invalidRows()} rather than
     * failing the whole read.
     *
     * @param path the CSV file to read
     * @return the parsed transfers and any invalid rows
     * @throws IOException if {@code path} cannot be read
     */
    public Result read(Path path) throws IOException {
        List<Transfer> transfers = new ArrayList<>();
        List<TransferResult> invalidRows = new ArrayList<>();
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank()) continue;
            try {
                transfers.add(parseLine(line));
            } catch (IllegalArgumentException e) {
                invalidRows.add(TransferResult.invalidRow(line, e.getMessage()));
            }
        }
        return new Result(transfers, invalidRows);
    }

    private Transfer parseLine(String line) {
        String[] fields = line.split(",", -1);
        if (fields.length != 3) {
            throw new IllegalArgumentException("expected 3 fields (from,to,amount), found " + fields.length);
        }
        String from = fields[0].trim();
        String to = fields[1].trim();
        Money amount = Money.fromDecimalString(fields[2].trim());
        if (amount.isNegative()) {
            throw new IllegalArgumentException("transfer amount must not be negative: " + amount);
        }
        return new Transfer(from, to, amount);
    }
}
