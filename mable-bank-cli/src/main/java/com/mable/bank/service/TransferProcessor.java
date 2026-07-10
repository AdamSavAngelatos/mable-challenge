package com.mable.bank.service;

import com.mable.bank.domain.Transfer;
import com.mable.bank.domain.TransferResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies a day's transfers to a {@link Ledger}, in file order, independently:
 * a rejected transfer (insufficient funds / unknown account) does not block
 * subsequent, unrelated transfers in the same batch.
 */
public final class TransferProcessor {

    private final Ledger ledger;

    public TransferProcessor(Ledger ledger) {
        this.ledger = ledger;
    }

    public List<TransferResult> process(List<Transfer> transfers) {
        List<TransferResult> results = new ArrayList<>(transfers.size());
        for (Transfer transfer : transfers) {
            results.add(ledger.applyTransfer(transfer));
        }
        return results;
    }
}
