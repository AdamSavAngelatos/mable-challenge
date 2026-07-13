package com.mable.bank.report;

import com.mable.bank.csv.AccountBalanceCsvReader;
import com.mable.bank.domain.Account;
import com.mable.bank.domain.Money;
import com.mable.bank.domain.TransferResult;
import com.mable.bank.domain.TransferStatus;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Formats the run's textual report as independent pieces -- a load summary,
 * a balances section, and the transfer audit trail. Each method returns a
 * plain String; Main composes them into the combined report.
 */
public final class ReportWriter {

    /**
     * Formats the outcome of loading the balances CSV.
     *
     * @param loadResult the outcome of reading the balances CSV
     * @return a summary line reporting how many accounts loaded, plus one line per
     *         rejected row (if any)
     */
    public String buildLoadSummary(AccountBalanceCsvReader.Result loadResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("Loaded ").append(loadResult.accounts().size()).append(" accounts");
        if (!loadResult.rejectedRows().isEmpty()) {
            sb.append(" (").append(loadResult.rejectedRows().size()).append(" row(s) rejected)");
        }
        sb.append(System.lineSeparator());
        loadResult.rejectedRows().forEach(row ->
                sb.append("  [REJECTED] ").append(row.rawRow()).append(" -> ").append(row.reason())
                        .append(System.lineSeparator()));
        return sb.toString();
    }

    /**
     * Formats the transfer-batch audit trail.
     *
     * @param results the outcome of each transfer in the batch, in file order
     * @return a "Transactions:" heading, one line per result, and a final counts summary;
     *         no balances section -- see {@link #buildBalancesSection}
     */
    public String buildTransferReport(List<TransferResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("Transactions:").append(System.lineSeparator());
        for (TransferResult result : results) {
            sb.append("  ").append(formatLine(result)).append(System.lineSeparator());
        }
        sb.append(System.lineSeparator())
                .append(formatSummary(results))
                .append(System.lineSeparator());
        return sb.toString();
    }

    /**
     * Renders a labeled, sorted account/balance listing -- used for both "Starting
     * balances" ({@code Account::getStartingBalance}) and "Closing balances"
     * ({@code Account::getClosingBalance}) against the same {@code List<Account>},
     * since each {@code Account} remembers both values for its whole lifetime.
     *
     * @param heading  the section heading, e.g. {@code "Starting balances"}
     * @param accounts the accounts to list, sorted by account number in the output
     * @param balance  selects which of an account's balances to render
     * @return the heading line followed by one indented "accountNumber: balance" line per account
     */
    public String buildBalancesSection(String heading, List<Account> accounts, Function<Account, Money> balance) {
        StringBuilder sb = new StringBuilder();
        sb.append(heading).append(":").append(System.lineSeparator());
        accounts.stream()
                .sorted(Comparator.comparing(Account::accountNumber))
                .forEach(account -> sb.append("  ")
                        .append(account.accountNumber())
                        .append(": ")
                        .append(balance.apply(account).toDecimalString())
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
