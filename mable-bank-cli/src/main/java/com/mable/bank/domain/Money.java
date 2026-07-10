package com.mable.bank.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Immutable currency amount. Wraps BigDecimal (not double) to avoid float-precision
 * drift, and enforces a single, consistent scale/rounding policy everywhere so it
 * never has to be repeated at each call site.
 */
public final class Money {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final BigDecimal amount;

    private Money(BigDecimal amount) {
        this.amount = amount.setScale(SCALE, ROUNDING);
    }

    public static Money of(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        return new Money(amount);
    }

    public static Money fromDecimalString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("amount must not be blank");
        }
        try {
            return new Money(new BigDecimal(value.trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not a valid decimal amount: " + value, e);
        }
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO);
    }

    public BigDecimal amount() {
        return amount;
    }

    public String toDecimalString() {
        return amount.toPlainString();
    }

    public Money add(Money other) {
        return new Money(this.amount.add(other.amount));
    }

    public Money subtract(Money other) {
        return new Money(this.amount.subtract(other.amount));
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isGreaterThanOrEqualTo(Money other) {
        return this.amount.compareTo(other.amount) >= 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money other)) return false;
        return amount.compareTo(other.amount) == 0;
    }

    @Override
    public int hashCode() {
        return amount.stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        return toDecimalString();
    }
}
