package com.mable.bank.report;

import com.mable.bank.domain.Account;
import com.mable.bank.domain.TransferResult;
import com.mable.bank.domain.TransferStatus;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Builds the human-readable audit trail: one line per transfer outcome plus a
 * final summary and closing balances. Both outputs (stdout, for the live demo,
 * and result.txt, the durable feedback artifact) are rendered from the single
 * {@link #buildReport} method, so they cannot drift apart the way two
 * independently-formatted methods could.
 */
public final class TransferReportWriter {

    public void printToStdout(List<TransferResult> results, List<Account> finalBalances, PrintStream out) {
        out.print(buildReport(results, finalBalances));
    }

    public void writeToFile(Path path, List<TransferResult> results, List<Account> finalBalances) throws IOException {
        Files.writeString(path, buildReport(results, finalBalances));
    }

    private String buildReport(List<TransferResult> results, List<Account> finalBalances) {
        StringBuilder sb = new StringBuilder();
        for (TransferResult result : results) {
            sb.append(formatLine(result)).append(System.lineSeparator());
        }
        sb.append(System.lineSeparator())
                .append(formatSummary(results))
                .append(System.lineSeparator())
                .append(System.lineSeparator())
                .append("Final balances:")
                .append(System.lineSeparator());

        finalBalances.stream()
                .sorted(Comparator.comparing(Account::accountNumber))
                .forEach(account -> sb.append("  ")
                        .append(account.accountNumber())
                        .append(": ")
                        .append(account.getBalance().toDecimalString())
                        .append(System.lineSeparator()));

        return sb.toString();
    }

    private String formatLine(TransferResult result) {
        if (result.status() == TransferStatus.INVALID_ROW) {
            return "[INVALID_ROW] " + result.rawRow() + " -> " + result.reason();
        }
        String base = "[" + result.status() + "] " + result.transfer().fromAccountNumber()
                + " -> " + result.transfer().toAccountNumber()
                + " $" + result.transfer().amount().toDecimalString();
        return result.reason() == null ? base : base + " (" + result.reason() + ")";
    }

    private String formatSummary(List<TransferResult> results) {
        long succeeded = results.stream().filter(r -> r.status() == TransferStatus.SUCCESS).count();
        long failed = results.size() - succeeded;
        return "Processed " + results.size() + " transfers: " + succeeded + " succeeded, " + failed + " failed.";
    }
}
