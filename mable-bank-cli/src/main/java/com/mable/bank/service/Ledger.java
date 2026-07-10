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

    private final Map<String, Account> accounts = new LinkedHashMap<>();

    public void loadBalances(List<Account> newAccounts) {
        accounts.clear();
        for (Account account : newAccounts) {
            accounts.put(account.accountNumber(), account);
        }
    }

    public Optional<Account> getAccount(String accountNumber) {
        return Optional.ofNullable(accounts.get(accountNumber));
    }

    public List<Account> listAccounts() {
        return List.copyOf(accounts.values());
    }

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
