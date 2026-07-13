package com.mable.bank.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A bank account. Tracks both its starting balance (frozen at construction,
 * never changes again) and its closing balance (mutated by debit()/credit()).
 */
public final class Account {

    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("\\d{16}");

    private final String accountNumber;
    private final Money startingBalance;
    private Money closingBalance;

    public Account(String accountNumber, Money startingBalance) {
        Objects.requireNonNull(accountNumber, "accountNumber must not be null");
        Objects.requireNonNull(startingBalance, "startingBalance must not be null");
        if (!ACCOUNT_NUMBER_PATTERN.matcher(accountNumber).matches()) {
            throw new IllegalArgumentException("accountNumber must be exactly 16 digits: " + accountNumber);
        }
        if (startingBalance.isNegative()) {
            throw new IllegalArgumentException("starting balance must not be negative: " + startingBalance);
        }
        this.accountNumber = accountNumber;
        this.startingBalance = startingBalance;
        this.closingBalance = startingBalance;
    }

    public String accountNumber() {
        return accountNumber;
    }

    public Money getStartingBalance() {
        return startingBalance;
    }

    public Money getClosingBalance() {
        return closingBalance;
    }

    public boolean canDebit(Money amount) {
        return closingBalance.isGreaterThanOrEqualTo(amount);
    }

    public void debit(Money amount) {
        if (!canDebit(amount)) {
            throw new InsufficientFundsException(accountNumber, closingBalance, amount);
        }
        closingBalance = closingBalance.subtract(amount);
    }

    public void credit(Money amount) {
        closingBalance = closingBalance.add(amount);
    }
}
