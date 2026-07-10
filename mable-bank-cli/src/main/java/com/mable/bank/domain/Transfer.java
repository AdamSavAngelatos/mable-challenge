package com.mable.bank.domain;

/**
 * One row of a day's transactions CSV. The compact constructor validates its own
 * invariants as defense-in-depth: it must be impossible to construct an invalid
 * Transfer from anywhere in the codebase, not only via the CSV reading path (which
 * independently validates before ever constructing one).
 */
public record Transfer(String fromAccountNumber, String toAccountNumber, Money amount) {

    public Transfer {
        if (fromAccountNumber == null || fromAccountNumber.isBlank()) {
            throw new IllegalArgumentException("fromAccountNumber must not be blank");
        }
        if (toAccountNumber == null || toAccountNumber.isBlank()) {
            throw new IllegalArgumentException("toAccountNumber must not be blank");
        }
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
    }
}
