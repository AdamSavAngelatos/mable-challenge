package com.mable.bank.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void parsesADecimalStringToTwoDecimalPlaces() {
        assertThat(Money.fromDecimalString("500").toDecimalString()).isEqualTo("500.00");
        assertThat(Money.fromDecimalString("500.5").toDecimalString()).isEqualTo("500.50");
        assertThat(Money.fromDecimalString("500.00").toDecimalString()).isEqualTo("500.00");
    }

    @Test
    void avoidsFloatingPointDriftOnRepeatedAddition() {
        Money tenCents = Money.fromDecimalString("0.10");
        Money twentyCents = Money.fromDecimalString("0.20");
        assertThat(tenCents.add(twentyCents)).isEqualTo(Money.fromDecimalString("0.30"));
    }

    @Test
    void addAndSubtractAreExact() {
        Money balance = Money.fromDecimalString("5000.00");
        Money debited = balance.subtract(Money.fromDecimalString("500.00"));
        assertThat(debited).isEqualTo(Money.fromDecimalString("4500.00"));
        assertThat(debited.add(Money.fromDecimalString("500.00"))).isEqualTo(balance);
    }

    @Test
    void isNegativeReflectsSign() {
        assertThat(Money.fromDecimalString("-1.00").isNegative()).isTrue();
        assertThat(Money.fromDecimalString("0.00").isNegative()).isFalse();
        assertThat(Money.fromDecimalString("1.00").isNegative()).isFalse();
    }

    @Test
    void isGreaterThanOrEqualToComparesValue() {
        Money five = Money.fromDecimalString("5.00");
        Money ten = Money.fromDecimalString("10.00");
        assertThat(ten.isGreaterThanOrEqualTo(five)).isTrue();
        assertThat(five.isGreaterThanOrEqualTo(five)).isTrue();
        assertThat(five.isGreaterThanOrEqualTo(ten)).isFalse();
    }

    @Test
    void rejectsBlankOrInvalidInput() {
        assertThatThrownBy(() -> Money.fromDecimalString(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.fromDecimalString("not-a-number"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void equalityIsByValueNotScale() {
        assertThat(Money.of(new BigDecimal("5"))).isEqualTo(Money.of(new BigDecimal("5.00")));
    }
}
