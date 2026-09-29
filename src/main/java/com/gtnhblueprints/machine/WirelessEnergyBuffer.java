package com.gtnhblueprints.machine;

import java.util.UUID;

/** Prepaid EU ledger. No world or player state is accessed here, making all balance transitions testable. */
final class WirelessEnergyBuffer {

    interface Ledger {
        boolean change(UUID account, long delta);
    }

    private UUID account;
    private long stored;

    static boolean deposit(UUID account, long amount, Ledger ledger) {
        return account != null && amount >= 0 && (amount == 0 || ledger.change(account, amount));
    }

    boolean consume(UUID requestedAccount, long amount, int reserveTicks, Ledger ledger) {
        if (requestedAccount == null || amount < 0) return false;
        if (amount == 0) return true;
        if (account != null && !account.equals(requestedAccount) && !refund(ledger)) return false;
        if (stored < amount) {
            int ticks = Math.max(1, Math.min(20, reserveTicks));
            long target = amount > Long.MAX_VALUE / ticks ? Long.MAX_VALUE : amount * ticks;
            long withdrawal = target - stored;
            if (!ledger.change(requestedAccount, -withdrawal)) {
                withdrawal = amount - stored;
                if (!ledger.change(requestedAccount, -withdrawal)) return false;
            }
            account = requestedAccount;
            stored += withdrawal;
        }
        stored -= amount;
        return true;
    }

    boolean refund(Ledger ledger) {
        if (stored > 0 && (account == null || !ledger.change(account, stored))) return false;
        stored = 0;
        account = null;
        return true;
    }

    long getStored() {
        return stored;
    }

    UUID getAccount() {
        return account;
    }

    void restore(UUID account, long stored) {
        if (stored < 0 || (stored > 0 && account == null)) {
            throw new IllegalArgumentException("Prepaid wireless energy requires a valid account and balance");
        }
        this.account = account;
        this.stored = stored;
    }
}
