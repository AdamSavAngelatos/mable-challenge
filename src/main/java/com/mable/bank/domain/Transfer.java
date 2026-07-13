package com.mable.bank.domain;

/**
 * One row of a day's transactions CSV. Every time a Transfer is created, its fields
 * are checked automatically -- so a Transfer with bad data (e.g. a blank account
 * number) can never exist, no matter where in the code it gets created. This holds
 * even though the CSV reader already checks each row itself before creating one; the
 * check here means nothing else in the codebase could accidentally skip that step.
 *
 * @param fromAccountNumber the account to debit; must not be blank
 * @param toAccountNumber   the account to credit; must not be blank
 * @param amount            the amount to move; must not be {@code null} or negative
 */
public record Transfer(String fromAccountNumber, String toAccountNumber, Money amount) {

    /**
     * Checks the fields whenever a Transfer is created.
     *
     * @throws IllegalArgumentException if {@code fromAccountNumber} or {@code toAccountNumber}
     *                                  is blank, or {@code amount} is {@code null} or negative.
     */
    public Transfer {
        if (fromAccountNumber == null || fromAccountNumber.isBlank()) {
            throw new IllegalArgumentException("fromAccountNumber must not be blank");
        }
        if (toAccountNumber == null || toAccountNumber.isBlank()) {
            throw new IllegalArgumentException("toAccountNumber must not be blank");
        }
        if (amount == null) {
            throw new IllegalArgumentException("transfer amount must not be null");
        }
        if (amount.isNegative()) {
            throw new IllegalArgumentException("transfer amount must not be negative: " + amount);
        }
    }
}
