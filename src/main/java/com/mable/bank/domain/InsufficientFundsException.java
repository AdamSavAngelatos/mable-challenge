package com.mable.bank.domain;

/**
 * Thrown by {@link Account#debit(Money)} if a debit would take the balance below $0.
 * Callers on the normal business path check {@link Account#canDebit(Money)} first
 * and never trigger this — it exists as a last-line invariant enforced by the model
 * itself, not as the mechanism for reporting an expected business rejection.
 */
public final class InsufficientFundsException extends RuntimeException {

    /**
     * Creates the exception with a message describing the failed debit.
     *
     * @param accountNumber    the account the debit was attempted against
     * @param availableBalance the account's balance at the time of the attempt
     * @param requestedAmount  the amount that was requested to be debited
     */
    public InsufficientFundsException(String accountNumber, Money availableBalance, Money requestedAmount) {
        super("Account " + accountNumber + " has insufficient funds: availableBalance=" + availableBalance
                + ", requested=" + requestedAmount);
    }
}
