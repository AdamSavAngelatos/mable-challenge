package com.mable.bank.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A bank account. The $0 floor is enforced here, inside debit(), so it is
 * impossible to construct a call path anywhere in the system that overdraws
 * an account.
 */
public final class Account {

    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("\\d{16}");

    private final String accountNumber;
    private Money balance;

    public Account(String accountNumber, Money balance) {
        Objects.requireNonNull(accountNumber, "accountNumber must not be null");
        Objects.requireNonNull(balance, "balance must not be null");
        if (!ACCOUNT_NUMBER_PATTERN.matcher(accountNumber).matches()) {
            throw new IllegalArgumentException("accountNumber must be exactly 16 digits: " + accountNumber);
        }
        if (balance.isNegative()) {
            throw new IllegalArgumentException("initial balance must not be negative: " + balance);
        }
        this.accountNumber = accountNumber;
        this.balance = balance;
    }

    public String accountNumber() {
        return accountNumber;
    }

    public Money getBalance() {
        return balance;
    }

    public boolean canDebit(Money amount) {
        return balance.isGreaterThanOrEqualTo(amount);
    }

    public void debit(Money amount) {
        if (!canDebit(amount)) {
            throw new InsufficientFundsException(accountNumber, balance, amount);
        }
        balance = balance.subtract(amount);
    }

    public void credit(Money amount) {
        balance = balance.add(amount);
    }
}
