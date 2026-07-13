package com.mable.bank.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private static final String VALID_ACCOUNT_NUMBER = "1111234522226789";

    @Test
    void debitReducesClosingBalance() {
        Account account = new Account(VALID_ACCOUNT_NUMBER, Money.fromDecimalString("100.00"));
        account.debit(Money.fromDecimalString("40.00"));
        assertThat(account.getClosingBalance()).isEqualTo(Money.fromDecimalString("60.00"));
    }

    @Test
    void creditIncreasesClosingBalance() {
        Account account = new Account(VALID_ACCOUNT_NUMBER, Money.fromDecimalString("100.00"));
        account.credit(Money.fromDecimalString("40.00"));
        assertThat(account.getClosingBalance()).isEqualTo(Money.fromDecimalString("140.00"));
    }

    @Test
    void canDebitExactlyDownToZero() {
        Account account = new Account(VALID_ACCOUNT_NUMBER, Money.fromDecimalString("100.00"));
        assertThat(account.canDebit(Money.fromDecimalString("100.00"))).isTrue();
        account.debit(Money.fromDecimalString("100.00"));
        assertThat(account.getClosingBalance()).isEqualTo(Money.zero());
    }

    @Test
    void cannotDebitBelowZero() {
        Account account = new Account(VALID_ACCOUNT_NUMBER, Money.fromDecimalString("100.00"));
        assertThat(account.canDebit(Money.fromDecimalString("100.01"))).isFalse();
        assertThatThrownBy(() -> account.debit(Money.fromDecimalString("100.01")))
                .isInstanceOf(InsufficientFundsException.class);
        // balance is unchanged after a rejected debit
        assertThat(account.getClosingBalance()).isEqualTo(Money.fromDecimalString("100.00"));
    }

    @Test
    void startingBalanceStaysFrozenWhileClosingBalanceChanges() {
        Account account = new Account(VALID_ACCOUNT_NUMBER, Money.fromDecimalString("100.00"));

        account.debit(Money.fromDecimalString("40.00"));
        account.credit(Money.fromDecimalString("10.00"));

        assertThat(account.getStartingBalance()).isEqualTo(Money.fromDecimalString("100.00"));
        assertThat(account.getClosingBalance()).isEqualTo(Money.fromDecimalString("70.00"));
    }

    @Test
    void startingAndClosingBalanceAreEqualImmediatelyAfterConstruction() {
        Account account = new Account(VALID_ACCOUNT_NUMBER, Money.fromDecimalString("100.00"));

        assertThat(account.getStartingBalance()).isEqualTo(account.getClosingBalance());
    }

    @Test
    void rejectsAccountNumberWithWrongLength() {
        // 5 digits, not 16
        assertThatThrownBy(() -> new Account("12345", Money.zero()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly 16 digits");
    }

    @Test
    void rejectsAccountNumberWithNonDigitCharacters() {
        // 16 characters, but the first four are letters, not digits
        assertThatThrownBy(() -> new Account("abcd234522226789", Money.zero()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly 16 digits");
    }

    @Test
    void rejectsNegativeStartingBalance() {
        assertThatThrownBy(() -> new Account(VALID_ACCOUNT_NUMBER, Money.fromDecimalString("-1.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
