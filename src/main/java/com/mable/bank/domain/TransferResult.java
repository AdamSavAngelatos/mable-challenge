package com.mable.bank.domain;

/**
 * The outcome of attempting to apply one transfer. {@code transfer} is null only for
 * {@link TransferStatus#INVALID_ROW}, since a row that failed to parse never produced
 * a valid {@link Transfer} in the first place — {@code rawRow} carries the original
 * CSV line in that case so the report can still point at exactly what was rejected.
 *
 * @param transfer the transfer this result describes; {@code null} for {@link TransferStatus#INVALID_ROW}
 * @param status   the outcome
 * @param reason   a human-readable explanation; {@code null} for {@link TransferStatus#SUCCESS}
 * @param rawRow   the original CSV line; non-null only for {@link TransferStatus#INVALID_ROW}
 */
public record TransferResult(Transfer transfer, TransferStatus status, String reason, String rawRow) {

    /**
     * Creates a successful result.
     *
     * @param transfer the transfer that was successfully applied
     * @return a {@link TransferStatus#SUCCESS} result for {@code transfer}
     */
    public static TransferResult success(Transfer transfer) {
        return new TransferResult(transfer, TransferStatus.SUCCESS, null, null);
    }

    /**
     * Creates a rejected result.
     *
     * @param transfer the transfer that was rejected
     * @param status   the rejection reason's status (never {@link TransferStatus#SUCCESS}
     *                 or {@link TransferStatus#INVALID_ROW})
     * @param reason   a human-readable explanation of the rejection
     * @return a rejected result for {@code transfer}
     */
    public static TransferResult rejected(Transfer transfer, TransferStatus status, String reason) {
        return new TransferResult(transfer, status, reason, null);
    }

    /**
     * Creates a result for a CSV row that could not be parsed into a {@link Transfer}.
     *
     * @param rawRow the original CSV line that failed to parse
     * @param reason a human-readable explanation of why it failed to parse
     * @return an {@link TransferStatus#INVALID_ROW} result with no {@link #transfer()}
     */
    public static TransferResult invalidRow(String rawRow, String reason) {
        return new TransferResult(null, TransferStatus.INVALID_ROW, reason, rawRow);
    }
}
