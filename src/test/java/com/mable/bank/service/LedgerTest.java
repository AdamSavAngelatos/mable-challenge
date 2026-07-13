package com.mable.bank.service;

import com.mable.bank.domain.Account;
import com.mable.bank.domain.Money;
import com.mable.bank.domain.Transfer;
import com.mable.bank.domain.TransferResult;
import com.mable.bank.domain.TransferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class LedgerTest {

    private static final String FROM = "1111234522226789";
    private static final String TO = "1212343433335665";
    private static final String UNKNOWN = "9999999999999999";

    private Ledger ledger;

    @BeforeEach
    void setUp() {
        ledger = new Ledger();
        ledger.loadBalances(List.of(
                new Account(FROM, Money.fromDecimalString("100.00")),
                new Account(TO, Money.fromDecimalString("50.00"))
        ));
    }

    @Test
    void applyTransferMovesMoneyBetweenAccountsOnSuccess() {
        TransferResult result = ledger.applyTransfer(new Transfer(FROM, TO, Money.fromDecimalString("30.00")));

        assertThat(result.status()).isEqualTo(TransferStatus.SUCCESS);
        assertThat(ledger.getAccount(FROM).orElseThrow().getClosingBalance()).isEqualTo(Money.fromDecimalString("70.00"));
        assertThat(ledger.getAccount(TO).orElseThrow().getClosingBalance()).isEqualTo(Money.fromDecimalString("80.00"));
    }

    @Test
    void applyTransferRejectsInsufficientFundsWithoutMovingMoney() {
        TransferResult result = ledger.applyTransfer(new Transfer(FROM, TO, Money.fromDecimalString("1000.00")));

        assertThat(result.status()).isEqualTo(TransferStatus.INSUFFICIENT_FUNDS);
        assertThat(ledger.getAccount(FROM).orElseThrow().getClosingBalance()).isEqualTo(Money.fromDecimalString("100.00"));
        assertThat(ledger.getAccount(TO).orElseThrow().getClosingBalance()).isEqualTo(Money.fromDecimalString("50.00"));
    }

    @Test
    void applyTransferRejectsUnknownFromAccount() {
        TransferResult result = ledger.applyTransfer(new Transfer(UNKNOWN, TO, Money.fromDecimalString("10.00")));
        assertThat(result.status()).isEqualTo(TransferStatus.UNKNOWN_ACCOUNT);
    }

    @Test
    void applyTransferRejectsUnknownToAccount() {
        TransferResult result = ledger.applyTransfer(new Transfer(FROM, UNKNOWN, Money.fromDecimalString("10.00")));
        assertThat(result.status()).isEqualTo(TransferStatus.UNKNOWN_ACCOUNT);
    }

    @Test
    void loadBalancesReplacesAllPriorState() {
        ledger.loadBalances(List.of(new Account(UNKNOWN, Money.fromDecimalString("5.00"))));

        assertThat(ledger.getAccount(FROM)).isEmpty();
        assertThat(ledger.getAccount(UNKNOWN)).isPresent();
        assertThat(ledger.listAccounts()).hasSize(1);
    }

    @Test
    void listAccountsExposesBothStartingAndClosingBalanceEvenAfterTransfersRun() {
        // Called *after* the transfer, deliberately -- proving getStartingBalance()
        // still reports the true original value (it's frozen at construction, on
        // Account itself), while getClosingBalance() reflects the mutation.
        ledger.applyTransfer(new Transfer(FROM, TO, Money.fromDecimalString("30.00")));

        List<Account> accounts = ledger.listAccounts();

        assertThat(accounts)
                .extracting(Account::accountNumber,
                        a -> a.getStartingBalance().toDecimalString(),
                        a -> a.getClosingBalance().toDecimalString())
                .containsExactlyInAnyOrder(
                        tuple(FROM, "100.00", "70.00"),
                        tuple(TO, "50.00", "80.00")
                );
    }
}
