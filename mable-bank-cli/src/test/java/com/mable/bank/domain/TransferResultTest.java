package com.mable.bank.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransferResultTest {

    private static final Transfer TRANSFER =
            new Transfer("1111234522226789", "1212343433335665", Money.fromDecimalString("10.00"));

    @Test
    void successFactoryProducesSuccessStatusWithNoReason() {
        TransferResult result = TransferResult.success(TRANSFER);
        assertThat(result.transfer()).isEqualTo(TRANSFER);
        assertThat(result.status()).isEqualTo(TransferStatus.SUCCESS);
        assertThat(result.reason()).isNull();
        assertThat(result.rawRow()).isNull();
    }

    @Test
    void rejectedFactoryCarriesStatusAndReason() {
        TransferResult result = TransferResult.rejected(TRANSFER, TransferStatus.INSUFFICIENT_FUNDS, "not enough funds");
        assertThat(result.transfer()).isEqualTo(TRANSFER);
        assertThat(result.status()).isEqualTo(TransferStatus.INSUFFICIENT_FUNDS);
        assertThat(result.reason()).isEqualTo("not enough funds");
    }

    @Test
    void invalidRowFactoryHasNoTransferButKeepsTheRawRow() {
        TransferResult result = TransferResult.invalidRow("bad,row", "expected 3 fields, found 2");
        assertThat(result.transfer()).isNull();
        assertThat(result.status()).isEqualTo(TransferStatus.INVALID_ROW);
        assertThat(result.rawRow()).isEqualTo("bad,row");
        assertThat(result.reason()).isEqualTo("expected 3 fields, found 2");
    }
}
