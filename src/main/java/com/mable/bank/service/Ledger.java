package com.mable.bank.service;

import com.mable.bank.domain.Account;
import com.mable.bank.domain.Transfer;
import com.mable.bank.domain.TransferResult;
import com.mable.bank.domain.TransferStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory store of accounts for a single company, keyed by account number.
 * The sole place that looks up both sides of a transfer and performs the
 * debit+credit — nothing else in the system mutates account balances.
 */
public final class Ledger {

    // LinkedHashMap, not HashMap: the ledger should faithfully mirror the order accounts
    // were loaded in (i.e. the CSV's own order), not an arbitrary hash-bucket order.
    private final Map<String, Account> accounts = new LinkedHashMap<>();

    /**
     * Replaces all currently loaded accounts with {@code newAccounts}.
     *
     * @param newAccounts the accounts to load -- discards, rather than merges with,
     *                    whatever was previously loaded
     */
    public void loadBalances(List<Account> newAccounts) {
        accounts.clear();
        for (Account account : newAccounts) {
            accounts.put(account.accountNumber(), account);
        }
    }

    public Optional<Account> getAccount(String accountNumber) {
        // Optional forces the caller to explicitly handle a realistic null lookup
        return Optional.ofNullable(accounts.get(accountNumber));
    }

    /**
     * Returns all currently loaded accounts.
     *
     * @return all currently loaded accounts, in an unspecified order
     */
    public List<Account> listAccounts() {
        return List.copyOf(accounts.values());
    }

    /**
     * Attempts to apply {@code transfer}: debits the source account and credits the
     * destination account if, and only if, both accounts exist and the source has
     * sufficient funds. Does not throw on a rejected transfer -- rejection is
     * reported through the returned {@link TransferResult}.
     *
     * @param transfer the transfer to attempt
     * @return the outcome: {@link TransferStatus#SUCCESS} if applied, otherwise the
     *         specific rejection reason
     */
    public TransferResult applyTransfer(Transfer transfer) {
        Account from = accounts.get(transfer.fromAccountNumber());
        if (from == null) {
            return TransferResult.rejected(transfer, TransferStatus.UNKNOWN_ACCOUNT,
                    "unknown account: " + transfer.fromAccountNumber());
        }
        Account to = accounts.get(transfer.toAccountNumber());
        if (to == null) {
            return TransferResult.rejected(transfer, TransferStatus.UNKNOWN_ACCOUNT,
                    "unknown account: " + transfer.toAccountNumber());
        }
        if (!from.canDebit(transfer.amount())) {
            return TransferResult.rejected(transfer, TransferStatus.INSUFFICIENT_FUNDS,
                    "account " + from.accountNumber() + " has insufficient funds for " + transfer.amount());
        }
        from.debit(transfer.amount());
        to.credit(transfer.amount());
        return TransferResult.success(transfer);
    }
}
