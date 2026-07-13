package com.mable.bank.report;

import com.mable.bank.csv.AccountBalanceCsvReader;
import com.mable.bank.domain.Account;
import com.mable.bank.domain.Money;
import com.mable.bank.domain.Transfer;
import com.mable.bank.domain.TransferResult;
import com.mable.bank.domain.TransferStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportWriterTest {

    private final ReportWriter writer = new ReportWriter();

    @Test
    void loadSummarySkipsTheRejectionNoteWhenAllRowsAreValid() {
        var result = new AccountBalanceCsvReader.Result(
                List.of(new Account("1111234522226789", Money.fromDecimalString("5000.00"))),
                List.of()
        );

        assertThat(writer.buildLoadSummary(result)).isEqualTo("Loaded 1 accounts" + System.lineSeparator());
    }

    @Test
    void loadSummaryReportsRejectedRowCountAndEachRejectedRow() {
        var result = new AccountBalanceCsvReader.Result(
                List.of(),
                List.of(new AccountBalanceCsvReader.RejectedRow("bad,row", "expected 2 fields, found 1"))
        );

        String summary = writer.buildLoadSummary(result);

        assertThat(summary).contains("Loaded 0 accounts (1 row(s) rejected)");
        assertThat(summary).contains("[REJECTED] bad,row -> expected 2 fields, found 1");
    }

    @Test
    void transferReportFormatsASuccessfulTransferLine() {
        Transfer transfer = new Transfer("1111234522226789", "1212343433335665", Money.fromDecimalString("500.00"));
        TransferResult result = TransferResult.success(transfer);

        String report = writer.buildTransferReport(List.of(result));

        assertThat(report).contains("[SUCCESS] 1111234522226789 -> 1212343433335665 $500.00");
        assertThat(report).contains("Processed 1 transfers: 1 succeeded, 0 failed.");
    }

    @Test
    void transferReportIncludesTheRejectionReasonForAFailedTransfer() {
        Transfer transfer = new Transfer("1111234522226789", "1212343433335665", Money.fromDecimalString("500.00"));
        TransferResult result = TransferResult.rejected(transfer, TransferStatus.INSUFFICIENT_FUNDS, "not enough funds");

        String report = writer.buildTransferReport(List.of(result));

        assertThat(report).contains("[INSUFFICIENT_FUNDS] 1111234522226789 -> 1212343433335665 $500.00 (not enough funds)");
    }

    @Test
    void transferReportFormatsAnInvalidRowByItsRawTextInsteadOfATransfer() {
        TransferResult result = TransferResult.invalidRow("bad,row", "expected 3 fields, found 2");

        String report = writer.buildTransferReport(List.of(result));

        assertThat(report).contains("[INVALID_ROW] bad,row -> expected 3 fields, found 2");
    }

    @Test
    void transferReportHasNoBalancesSection() {
        // buildTransferReport's job is the audit trail only -- rendering a balances
        // section is buildBalancesSection's responsibility, called separately by Main.
        Transfer transfer = new Transfer("1111234522226789", "1212343433335665", Money.fromDecimalString("500.00"));

        String report = writer.buildTransferReport(List.of(TransferResult.success(transfer)));

        assertThat(report).doesNotContain("Starting balances").doesNotContain("Closing balances");
    }

    @Test
    void balancesSectionUsesTheGivenHeadingAndSortsByAccountNumber() {
        List<Account> accounts = List.of(
                new Account("3212343433335755", Money.fromDecimalString("50000.00")),
                new Account("1111234522226789", Money.fromDecimalString("5000.00"))
        );

        String section = writer.buildBalancesSection("Starting balances", accounts, Account::getStartingBalance);

        assertThat(section).isEqualTo(
                "Starting balances:" + System.lineSeparator()
                        + "  1111234522226789: 5000.00" + System.lineSeparator()
                        + "  3212343433335755: 50000.00" + System.lineSeparator()
        );
    }

    @Test
    void balancesSectionCanReportTheClosingBalanceInstead() {
        Account account = new Account("1111234522226789", Money.fromDecimalString("5000.00"));
        account.debit(Money.fromDecimalString("179.50"));

        String section = writer.buildBalancesSection("Closing balances", List.of(account), Account::getClosingBalance);

        assertThat(section).isEqualTo(
                "Closing balances:" + System.lineSeparator()
                        + "  1111234522226789: 4820.50" + System.lineSeparator()
        );
    }
}
