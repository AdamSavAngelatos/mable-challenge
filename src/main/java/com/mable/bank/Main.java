package com.mable.bank;

import com.mable.bank.csv.AccountBalanceCsvReader;
import com.mable.bank.csv.AccountBalanceCsvWriter;
import com.mable.bank.csv.TransferCsvReader;
import com.mable.bank.domain.Account;
import com.mable.bank.domain.TransferResult;
import com.mable.bank.report.ReportWriter;
import com.mable.bank.service.Ledger;
import com.mable.bank.service.TransferProcessor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * CLI entrypoint. No DI framework: the whole object graph is a handful of
 * classes wired by hand here, which is clearer at this scale than any
 * framework convention would be.
 *
 * Usage: java -jar mable-bank-cli.jar <balances.csv> <transactions.csv> [outputDir]
 */
public final class Main {

    public static void main(String[] args) {
        if (args.length < 2 || args.length > 3) {
            System.err.println("Usage: java -jar mable-bank-cli.jar <balances.csv> <transactions.csv> [outputDir]");
            System.exit(1);
        }

        Path balancesPath = Path.of(args[0]);
        Path transactionsPath = Path.of(args[1]);
        Path outputDir = args.length == 3 ? Path.of(args[2]) : Path.of(".");

        try {
            run(balancesPath, transactionsPath, outputDir, System.out);
        } catch (IOException e) {
            System.err.println("Failed to read/write files: " + e.getMessage());
            System.exit(1);
        }
    }

    static void run(Path balancesPath, Path transactionsPath, Path outputDir, java.io.PrintStream out) throws IOException {
        // Wire up the object graph by hand -- no DI framework needed at this size.
        Ledger ledger = new Ledger();
        TransferProcessor transferProcessor = new TransferProcessor(ledger);
        AccountBalanceCsvReader balanceReader = new AccountBalanceCsvReader();
        AccountBalanceCsvWriter balanceWriter = new AccountBalanceCsvWriter();
        TransferCsvReader transferReader = new TransferCsvReader();
        ReportWriter reportWriter = new ReportWriter();

        // Load starting balances into the ledger.
        AccountBalanceCsvReader.Result loadResult = balanceReader.read(balancesPath);
        ledger.loadBalances(loadResult.accounts());

        // Read the transfers csv data
        TransferCsvReader.Result transferReadResult = transferReader.read(transactionsPath);
        // Apply the transfers
        List<TransferResult> results = new ArrayList<>(transferProcessor.process(transferReadResult.transfers()));
        // Add back any original transfer data that failed to parse
        results.addAll(transferReadResult.invalidRows());

        List<Account> accounts = ledger.listAccounts();

        // Compose one combined report (starting balances, transfer audit trail and closing balances) and write it identically to stdout and result.txt
        String report = reportWriter.buildLoadSummary(loadResult)
                + System.lineSeparator()
                + reportWriter.buildBalancesSection("Starting balances", accounts, Account::getStartingBalance)
                + System.lineSeparator()
                + reportWriter.buildTransferReport(results)
                + System.lineSeparator()
                + reportWriter.buildBalancesSection("Closing balances", accounts, Account::getClosingBalance);
        out.print(report);

        java.nio.file.Files.createDirectories(outputDir);
        // Main output of program - the updated csv and persistent audit trail
        balanceWriter.write(outputDir.resolve("updated-account-balances.csv"), accounts);
        java.nio.file.Files.writeString(outputDir.resolve("result.txt"), report);
    }

    private Main() {
    }
}
