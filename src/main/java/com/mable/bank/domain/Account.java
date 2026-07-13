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

    /**
     * Creates an account with the given starting balance.
     *
     * @param accountNumber   a 16-digit account number; must not be {@code null}
     * @param startingBalance the account's opening balance; must not be {@code null} or negative
     * @throws NullPointerException     if {@code accountNumber} or {@code startingBalance} is {@code null}
     * @throws IllegalArgumentException if {@code accountNumber} is not exactly 16 digits, or
     *                                  {@code startingBalance} is negative
     */
    public Account(String accountNumber, Money startingBalance) {
        // NullPointerException, not IllegalArgumentException, deliberately: a null here means
        // a caller bug, not bad CSV data (the CSV path never produces null), so it should
        // propagate and crash loudly rather than be caught by AccountBalanceCsvReader's
        // catch (IllegalArgumentException) and silently reported as just another rejected row.
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

    /**
     * Returns this account's starting balance.
     *
     * @return the balance this account was constructed with; never changes after construction
     */
    public Money getStartingBalance() {
        return startingBalance;
    }

    /**
     * Returns this account's closing balance.
     *
     * @return this account's current balance, as of the most recent {@link #debit} or {@link #credit}
     */
    public Money getClosingBalance() {
        return closingBalance;
    }

    /**
     * Checks {@code amount} against the $0 floor, without changing the balance.
     *
     * @param amount the amount to check; must not be {@code null}
     * @return {@code true} if debiting {@code amount} would leave the balance at or above zero
     */
    public boolean canDebit(Money amount) {
        return closingBalance.isGreaterThanOrEqualTo(amount);
    }

    /**
     * Reduces the closing balance by {@code amount}.
     *
     * @param amount the amount to debit; must not be {@code null}
     * @throws InsufficientFundsException if {@code amount} exceeds the current closing balance.
     *         Callers on the normal business path check {@link #canDebit} first and should never
     *         trigger this -- see {@link InsufficientFundsException}'s javadoc.
     */
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
