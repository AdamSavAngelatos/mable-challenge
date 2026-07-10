package com.mable.bank.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferTest {

    private static final Money AMOUNT = Money.fromDecimalString("10.00");

    @Test
    void storesItsFields() {
        Transfer transfer = new Transfer("1111234522226789", "1212343433335665", AMOUNT);
        assertThat(transfer.fromAccountNumber()).isEqualTo("1111234522226789");
        assertThat(transfer.toAccountNumber()).isEqualTo("1212343433335665");
        assertThat(transfer.amount()).isEqualTo(AMOUNT);
    }

    @Test
    void rejectsNullOrBlankAccountNumbers() {
        assertThatThrownBy(() -> new Transfer(null, "1212343433335665", AMOUNT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Transfer(" ", "1212343433335665", AMOUNT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Transfer("1111234522226789", null, AMOUNT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Transfer("1111234522226789", " ", AMOUNT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullAmount() {
        assertThatThrownBy(() -> new Transfer("1111234522226789", "1212343433335665", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
