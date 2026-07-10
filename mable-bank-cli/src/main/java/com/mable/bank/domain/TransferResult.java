package com.mable.bank.domain;

/**
 * The outcome of attempting to apply one transfer. {@code transfer} is null only for
 * {@link TransferStatus#INVALID_ROW}, since a row that failed to parse never produced
 * a valid {@link Transfer} in the first place — {@code rawRow} carries the original
 * CSV line in that case so the report can still point at exactly what was rejected.
 */
public record TransferResult(Transfer transfer, TransferStatus status, String reason, String rawRow) {

    public static TransferResult success(Transfer transfer) {
        return new TransferResult(transfer, TransferStatus.SUCCESS, null, null);
    }

    public static TransferResult rejected(Transfer transfer, TransferStatus status, String reason) {
        return new TransferResult(transfer, status, reason, null);
    }

    public static TransferResult invalidRow(String rawRow, String reason) {
        return new TransferResult(null, TransferStatus.INVALID_ROW, reason, rawRow);
    }
}
