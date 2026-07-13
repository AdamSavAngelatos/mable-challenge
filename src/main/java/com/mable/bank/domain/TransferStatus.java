package com.mable.bank.domain;

/**
 * The outcome of attempting to apply one {@link Transfer}.
 */
public enum TransferStatus {
    /** The transfer was applied: the source account was debited and the destination credited. */
    SUCCESS,
    /** The source account's balance was too low to cover the transfer. */
    INSUFFICIENT_FUNDS,
    /** The source or destination account number does not exist in the loaded ledger. */
    UNKNOWN_ACCOUNT,
    /** The CSV row could not be parsed into a {@link Transfer} at all. */
    INVALID_ROW
}
