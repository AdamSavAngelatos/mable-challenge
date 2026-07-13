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
 * <p>Usage: {@code java -jar mable-bank-cli.jar <balances.csv> <transactions.csv> [outputDir]}
 */
public final class Main {

    /**
     * Parses arguments and runs the CLI.
     *
     * @param args {@code <balances.csv> <transactions.csv> [outputDir]} -- exits with
     *             status 1 and a usage message on stderr if the argument count is wrong
     */
    public static void main(String[] args) {
        try {
            Args parsedArgs = parseArgs(args);
            run(parsedArgs.balancesPath(), parsedArgs.transactionsPath(), parsedArgs.outputDir(), System.out);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Failed to read/write files: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * The parsed, ready-to-use form of {@code main}'s {@code String[] args}.
     *
     * @param balancesPath     the balances CSV to load
     * @param transactionsPath the day's transactions CSV to apply
     * @param outputDir        directory the output files are written to; the current
     *                         directory if not given on the command line
     */
    record Args(Path balancesPath, Path transactionsPath, Path outputDir) {
    }

    /**
     * Parses and validates {@code main}'s raw arguments. Kept separate from {@code main}
     * itself, which can't be unit tested directly since a failed parse ends in
     * {@code System.exit}.
     *
     * @param args {@code <balances.csv> <transactions.csv> [outputDir]}
     * @return the parsed arguments, with {@code outputDir} defaulted to {@code "."}
     *         if not given
     * @throws IllegalArgumentException if {@code args} has fewer than 2 or more than 3 elements;
     *                                   the message is the usage line to print on stderr
     */
    static Args parseArgs(String[] args) {
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException(
                    "Usage: java -jar mable-bank-cli.jar <balances.csv> <transactions.csv> [outputDir]");
        }
        Path balancesPath = Path.of(args[0]);
        Path transactionsPath = Path.of(args[1]);
        Path outputDir = args.length == 3 ? Path.of(args[2]) : Path.of(".");
        return new Args(balancesPath, transactionsPath, outputDir);
    }

    /**
     * Loads balances, applies the day's transfers, and writes every output artifact:
     * the combined report to {@code out} and to {@code outputDir/result.txt}, and the
     * closing balances to {@code outputDir/updated-account-balances.csv}. Separate from
     * {@link #main} specifically so {@code MainIntegrationTest} can call it directly with
     * an injected {@code PrintStream}, bypassing {@code main}'s argument parsing and
     * {@code System.exit} calls.
     *
     * @param balancesPath     the balances CSV to load
     * @param transactionsPath the day's transactions CSV to apply
     * @param outputDir        directory the output files are written to; created if missing
     * @param out              where the report is printed live (real {@code System.out} in
     *                         production, an injected stream in tests)
     * @throws IOException if any input file cannot be read or output file cannot be written
     */
    static void run(Path balancesPath, Path transactionsPath, Path outputDir, java.io.PrintStream out) throws IOException {
        // Wire up the object graph by hand -- no DI framework needed at this size.
        Ledger ledger = new Ledger();
        TransferProcessor transferProcessor = new TransferProcessor(ledger);
        AccountBalanceCsvReader balanceReader = new AccountBalanceCsvReader();
        AccountBalanceCsvWriter balanceWriter = new AccountBalanceCsvWriter();
        TransferCsvReader transferReader = new TransferCsvReader();
        ReportWriter reportWriter = new ReportWriter();

        // Load starting balances into the ledger.
        AccountBalanceCsvReader.Result balances = balanceReader.read(balancesPath);
        ledger.loadBalances(balances.accounts());

        // Read the transfers csv data
        TransferCsvReader.Result transfers = transferReader.read(transactionsPath);

        // TODO: results below are not in true transactions.csv file order.
        // Valid and invalid rows are reported as two separate groups (all processed rows,
        // then all invalid rows) instead of interleaved as they appeared in the file --
        // e.g. a file [valid, invalid, valid] reports as [valid, valid, invalid].
        // Fix: have TransferCsvReader tag each row with its original line number as it's
        // parsed, then sort the combined results by that number before building the report.

        // Apply the transfers
        List<TransferResult> results = new ArrayList<>(transferProcessor.process(transfers.transfers()));
        // Add back any original transfer data that failed to parse
        results.addAll(transfers.invalidRows());

        List<Account> accounts = ledger.listAccounts();

        // Compose one combined report (starting balances, transfer audit trail and closing balances) and write it identically to stdout and result.txt
        String report = reportWriter.buildLoadSummary(balances)
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
