package com.mable.bank;

import com.mable.bank.csv.AccountBalanceCsvReader;
import com.mable.bank.csv.AccountBalanceCsvWriter;
import com.mable.bank.csv.TransferCsvReader;
import com.mable.bank.domain.TransferResult;
import com.mable.bank.report.TransferReportWriter;
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
        Ledger ledger = new Ledger();
        TransferProcessor transferProcessor = new TransferProcessor(ledger);
        AccountBalanceCsvReader balanceReader = new AccountBalanceCsvReader();
        AccountBalanceCsvWriter balanceWriter = new AccountBalanceCsvWriter();
        TransferCsvReader transferReader = new TransferCsvReader();
        TransferReportWriter reportWriter = new TransferReportWriter();

        AccountBalanceCsvReader.Result loadResult = balanceReader.read(balancesPath);
        out.println("Loaded " + loadResult.accounts().size() + " accounts"
                + (loadResult.rejectedRows().isEmpty() ? "" : " (" + loadResult.rejectedRows().size() + " rows rejected)"));
        loadResult.rejectedRows().forEach(row -> out.println("  [REJECTED] " + row));
        ledger.loadBalances(loadResult.accounts());

        TransferCsvReader.Result transferReadResult = transferReader.read(transactionsPath);
        List<TransferResult> results = new ArrayList<>(transferProcessor.process(transferReadResult.transfers()));
        results.addAll(transferReadResult.invalidRows());

        reportWriter.printToStdout(results, out);

        java.nio.file.Files.createDirectories(outputDir);
        balanceWriter.write(outputDir.resolve("updated-account-balances.csv"), ledger.listAccounts());
        reportWriter.writeToFile(outputDir.resolve("result.txt"), results, ledger.listAccounts());
    }

    private Main() {
    }
}
