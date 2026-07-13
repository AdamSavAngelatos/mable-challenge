package com.mable.bank.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void parsesADecimalStringToTwoDecimalPlaces() {
        // None of these inputs have more than 2 decimal places, so this only proves
        // zero-padding to scale 2 -- it doesn't exercise rounding at all. See
        // roundsToTwoDecimalPlacesUsingHalfUp for that.
        assertThat(Money.fromDecimalString("500").toDecimalString()).isEqualTo("500.00");
        assertThat(Money.fromDecimalString("500.5").toDecimalString()).isEqualTo("500.50");
        assertThat(Money.fromDecimalString("500.00").toDecimalString()).isEqualTo("500.00");
    }

    @Test
    void roundsToTwoDecimalPlacesUsingHalfUp() {
        // 5.005 specifically distinguishes HALF_UP from BigDecimal's other common rounding
        // modes: HALF_EVEN (banker's rounding) would round this down to 5.00, since 0 is
        // already even. HALF_UP always rounds an exact half away from zero, so this must
        // round up to 5.01. A non-half case like 5.067 would round to 5.07 under any of
        // these modes, so it wouldn't actually prove which one is in effect.
        assertThat(Money.fromDecimalString("5.005").toDecimalString()).isEqualTo("5.01");
    }

    @Test
    void avoidsFloatingPointDriftOnRepeatedAddition() {
        Money tenCents = Money.fromDecimalString("0.10");
        Money twentyCents = Money.fromDecimalString("0.20");
        Money fiveCents = Money.fromDecimalString("0.05");
        assertThat(tenCents.add(twentyCents).add(fiveCents)).isEqualTo(Money.fromDecimalString("0.35"));
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
        Money fiveDollars = Money.fromDecimalString("5.00");
        Money tenDollars = Money.fromDecimalString("10.00");
        assertThat(tenDollars.isGreaterThanOrEqualTo(fiveDollars)).isTrue();
        assertThat(fiveDollars.isGreaterThanOrEqualTo(fiveDollars)).isTrue();
        assertThat(fiveDollars.isGreaterThanOrEqualTo(tenDollars)).isFalse();
    }

    @Test
    void fromDecimalStringRejectsBlankInput() {
        assertThatThrownBy(() -> Money.fromDecimalString(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void fromDecimalStringRejectsNonNumericInput() {
        assertThatThrownBy(() -> Money.fromDecimalString("not-a-number"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a valid decimal amount");
    }

    @Test
    void ofRejectsNullAmount() {
        assertThatThrownBy(() -> Money.of(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    void equalityIsByValueNotScale() {
        assertThat(Money.of(new BigDecimal("5"))).isEqualTo(Money.of(new BigDecimal("5.00")));
    }
}
