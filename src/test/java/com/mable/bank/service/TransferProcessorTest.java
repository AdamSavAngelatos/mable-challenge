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

class TransferProcessorTest {

    private static final String A = "1111234522226789";
    private static final String B = "1212343433335665";
    private static final String C = "2222123433331212";
    private static final String D = "3212343433335755";
    private static final String E = "1111234522221234";

    private Ledger ledger;
    private TransferProcessor processor;

    @BeforeEach
    void setUp() {
        ledger = new Ledger();
        ledger.loadBalances(List.of(
                new Account(A, Money.fromDecimalString("100.00")),
                new Account(B, Money.zero()),
                new Account(C, Money.zero()),
                new Account(D, Money.fromDecimalString("50.00")),
                new Account(E, Money.zero())
        ));
        processor = new TransferProcessor(ledger);
    }

    @Test
    void appliesTransfersInFileOrderSoLaterTransfersSeeEarlierEffects() {
        // A starts with exactly 100: the first transfer drains it completely,
        // so the second transfer (also from A) must be rejected -- this only
        // holds if transfers are applied strictly in order.
        List<TransferResult> results = processor.process(List.of(
                new Transfer(A, B, Money.fromDecimalString("100.00")),
                new Transfer(A, C, Money.fromDecimalString("10.00"))
        ));

        assertThat(results.get(0).status()).isEqualTo(TransferStatus.SUCCESS);
        assertThat(results.get(1).status()).isEqualTo(TransferStatus.INSUFFICIENT_FUNDS);
    }

    @Test
    void aRejectedTransferDoesNotBlockSubsequentUnrelatedTransfers() {
        List<TransferResult> results = processor.process(List.of(
                new Transfer(A, B, Money.fromDecimalString("100.00")), // succeeds, drains A
                new Transfer(A, C, Money.fromDecimalString("10.00")),  // rejected: insufficient funds
                new Transfer(D, E, Money.fromDecimalString("5.00"))    // unrelated -- must still succeed
        ));

        assertThat(results).extracting(TransferResult::status)
                .containsExactly(TransferStatus.SUCCESS, TransferStatus.INSUFFICIENT_FUNDS, TransferStatus.SUCCESS);
        assertThat(ledger.getAccount(D).orElseThrow().getClosingBalance()).isEqualTo(Money.fromDecimalString("45.00"));
        assertThat(ledger.getAccount(E).orElseThrow().getClosingBalance()).isEqualTo(Money.fromDecimalString("5.00"));
    }

    @Test
    void emptyBatchProducesNoResults() {
        assertThat(processor.process(List.of())).isEmpty();
    }
}
