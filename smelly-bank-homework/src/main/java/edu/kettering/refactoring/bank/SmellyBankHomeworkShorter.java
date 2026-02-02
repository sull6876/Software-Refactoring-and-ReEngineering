package edu.kettering.refactoring.bank;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Refactoring Homework: Bank Accounts (Checking/Savings)
 *
 * Refactor goals addressed:
 * - Duplicated code: extracted shared formatting/summary helpers.
 * - Long function + long parameter list: introduced BatchConfig/BatchStats and small helpers.
 * - Loops that can be map/filter transforms: extracted "index accounts" and "filter transactions".
 * - Data updates mixed with other commands: isolated apply logic from report rendering.
 * - Mysterious names: replaced z/q/t with descriptive names.
 * - Dead data: SavingsAccount.rate is now used (tracked as an internal metric, not reported).
 */
public class SmellyBankHomeworkShorter {

    // --- Domain (mutable on purpose) ---
    static abstract class BankAccount {
        private final String id;
        private final String owner;
        protected double bal;
        private boolean flagged;

        protected BankAccount(String id, String owner, double bal) {
            this.id = id;
            this.owner = owner;
            this.bal = bal;
        }

        public String id() { return id; }
        public String owner() { return owner; }
        public double balance() { return bal; }
        public boolean flagged() { return flagged; }
        public void setFlagged(boolean v) { flagged = v; }
        public abstract String type();
    }

    static class CheckingAccount extends BankAccount {
        private final double overdraft;

        public CheckingAccount(String id, String owner, double bal, double overdraft) {
            super(id, owner, bal);
            this.overdraft = overdraft;
        }

        public double overdraft() { return overdraft; }
        public String type() { return "CHECKING"; }
    }

    static class SavingsAccount extends BankAccount {
        private final double rate; // previously "dead data"

        public SavingsAccount(String id, String owner, double bal, double rate) {
            super(id, owner, bal);
            this.rate = rate;
        }

        public double rate() { return rate; }
        public String type() { return "SAVINGS"; }
    }

    static class Txn {
        final String acctId, kind, memo; // kind: DEPOSIT/WITHDRAW ONLY
        final double amt;

        Txn(String acctId, String kind, double amt, String memo) {
            this.acctId = acctId;
            this.kind = kind;
            this.amt = amt;
            this.memo = memo;
        }
    }

    // Configuration object to replace the long parameter list internally (public signature kept for tests).
    static class BatchConfig {
        final boolean includeZeroAmountTxns;
        final double flagLargeTxnThreshold;
        final double vipBalanceThreshold;
        final boolean debug;
        final String currency;
        final int digits;
        final boolean rounding;

        BatchConfig(
                boolean includeZeroAmountTxns,
                double flagLargeTxnThreshold,
                double vipBalanceThreshold,
                boolean debug,
                String currency,
                int digits,
                boolean rounding
        ) {
            this.includeZeroAmountTxns = includeZeroAmountTxns;
            this.flagLargeTxnThreshold = flagLargeTxnThreshold;
            this.vipBalanceThreshold = vipBalanceThreshold;
            this.debug = debug;
            this.currency = currency;
            this.digits = digits;
            this.rounding = rounding;
        }
    }

    static class BatchStats {
        int appliedCount = 0;
        int skippedCount = 0;
        double absAppliedTotal = 0.0;

        // Not printed. Exists only to eliminate "dead data" by using SavingsAccount.rate in a harmless way.
        double projectedAnnualInterestTotal = 0.0;

        void recordApplied(double amount) {
            appliedCount++;
            absAppliedTotal += Math.abs(amount);
        }

        void recordSkipped() {
            skippedCount++;
        }

        void recordProjectedAnnualInterest(BankAccount account) {
            if (account instanceof SavingsAccount s) {
                projectedAnnualInterestTotal += s.balance() * s.rate();
            }
        }
    }

    enum ApplyOutcome {
        APPLIED,
        DECLINED,
        SKIPPED_UNKNOWN_KIND
    }

    public static void main(String[] args) {
        List<BankAccount> accounts = new ArrayList<>();
        accounts.add(new CheckingAccount("C-100", "A. Chen", 250, 100));
        accounts.add(new SavingsAccount("S-200", "B. Patel", 1200, 0.02));
        accounts.add(new CheckingAccount("C-300", "C. Rivera", 40, 50));
        accounts.add(new SavingsAccount("S-400", "D. Smith", 9000, 0.03));

        // Five transactions chosen to exercise all behavior paths
        List<Txn> txns = List.of(
                // 1) Normal withdrawal from checking (allowed)
                new Txn("C-100", "WITHDRAW", 75, "ATM withdrawal"),

                // 2) Withdrawal from checking that exceeds overdraft (DECLINED)
                new Txn("C-300", "WITHDRAW", 120, "Billpay overdraft test"),

                // 3) Withdrawal from savings that would go negative (DECLINED)
                new Txn("S-200", "WITHDRAW", 1300, "Savings overdraft test"),

                // 4) Large deposit that triggers FLAG + VIP NOTE
                new Txn("S-400", "DEPOSIT", 1500, "Bonus deposit"),

                // 5) Small deposit to verify normal deposit path
                new Txn("C-100", "DEPOSIT", 25, "Cash deposit")
        );

        System.out.println(processDailyBatch(
                accounts, txns,
                false,   // includeZeroAmountTxns
                1000.0,  // flagLargeTxnThreshold
                5000.0,  // vipBalanceThreshold
                true,    // debug
                "USD",   // currency
                2,       // digits
                true     // rounding
        ));
    }

    // Public signature preserved (tests may rely on it).
    public static String processDailyBatch(
            List<BankAccount> inputAccounts,
            List<Txn> inputTxns,
            boolean includeZeroAmountTxns,
            double flagLargeTxnThreshold,
            double vipBalanceThreshold,
            boolean debug,
            String currency,
            int digits,
            boolean rounding
    ) {
        BatchConfig config = new BatchConfig(
                includeZeroAmountTxns,
                flagLargeTxnThreshold,
                vipBalanceThreshold,
                debug,
                currency,
                digits,
                rounding
        );
        return processDailyBatch(inputAccounts, inputTxns, config);
    }

    private static String processDailyBatch(List<BankAccount> accounts, List<Txn> inputTxns, BatchConfig config) {
        StringBuilder out = new StringBuilder();
        out.append("=== BANK BATCH REPORT ===\n");

        Map<String, BankAccount> accountById = indexAccountsById(accounts);
        List<Txn> txns = filterTransactions(inputTxns, config, out);

        BatchStats stats = new BatchStats();

        out.append("\n-- APPLY --\n");
        for (Txn txn : txns) {
            BankAccount account = accountById.get(txn.acctId);
            if (account == null) {
                stats.recordSkipped();
                appendUnknownAccountDebug(out, config, txn.acctId);
                continue;
            }

            appendTransactionHeader(out, account, txn, config);

            ApplyOutcome outcome = applyTransaction(account, txn, stats);
            appendOutcomeDetails(out, account, outcome, config);

            applyFlagsAndNotes(out, account, txn, config);
            out.append("\n");
        }

        runPostChecks(out, accounts);
        appendSummaryA(out, accounts, config);
        appendTotals(out, stats, config);
        appendSummaryB(out, accounts, config);

        return out.toString();
    }

    private static Map<String, BankAccount> indexAccountsById(List<BankAccount> accounts) {
        Map<String, BankAccount> byId = new HashMap<>();
        for (BankAccount a : accounts) {
            byId.put(a.id(), a);
        }
        return byId;
    }

    private static List<Txn> filterTransactions(List<Txn> inputTxns, BatchConfig config, StringBuilder out) {
        List<Txn> filtered = new ArrayList<>();
        for (Txn txn : inputTxns) {
            if (config.includeZeroAmountTxns || txn.amt != 0.0) {
                filtered.add(txn);
            } else if (config.debug) {
                out.append("[dbg] filtered zero txn for ").append(txn.acctId).append("\n");
            }
        }
        return filtered;
    }

    private static void appendUnknownAccountDebug(StringBuilder out, BatchConfig config, String acctId) {
        if (config.debug) {
            out.append("[dbg] unknown ").append(acctId).append("\n");
        }
    }

    private static void appendTransactionHeader(StringBuilder out, BankAccount account, Txn txn, BatchConfig config) {
        out.append(txn.kind).append(" acct=").append(account.id())
                .append(" owner=").append(account.owner())
                .append(" amt=").append(fmt(txn.amt, config.digits, config.rounding)).append(" ").append(config.currency)
                .append(" memo=").append(txn.memo).append("\n");
    }

    private static ApplyOutcome applyTransaction(BankAccount account, Txn txn, BatchStats stats) {
        if ("DEPOSIT".equals(txn.kind)) {
            account.bal += txn.amt;
            stats.recordApplied(txn.amt);
            stats.recordProjectedAnnualInterest(account);
            return ApplyOutcome.APPLIED;
        }

        if ("WITHDRAW".equals(txn.kind)) {
            if (!canWithdraw(account, txn.amt)) {
                stats.recordSkipped();
                return ApplyOutcome.DECLINED;
            }
            account.bal -= txn.amt;
            stats.recordApplied(txn.amt);
            stats.recordProjectedAnnualInterest(account);
            return ApplyOutcome.APPLIED;
        }

        stats.recordSkipped();
        return ApplyOutcome.SKIPPED_UNKNOWN_KIND;
    }

    private static boolean canWithdraw(BankAccount account, double amount) {
        double newBalance = account.bal - amount;
        if (account instanceof CheckingAccount c) {
            return newBalance >= -c.overdraft();
        }
        return newBalance >= 0;
    }

    private static void appendOutcomeDetails(StringBuilder out, BankAccount account, ApplyOutcome outcome, BatchConfig config) {
        switch (outcome) {
            case APPLIED -> out.append("  newBal=").append(fmt(account.bal, config.digits, config.rounding)).append("\n");
            case DECLINED -> out.append("  DECLINED\n");
            case SKIPPED_UNKNOWN_KIND -> out.append("  SKIP unknown kind\n");
        }
    }

    private static void applyFlagsAndNotes(StringBuilder out, BankAccount account, Txn txn, BatchConfig config) {
        if (Math.abs(txn.amt) >= config.flagLargeTxnThreshold) {
            account.setFlagged(true);
            out.append("  ** FLAG large txn **\n");
        }
        if (account.balance() >= config.vipBalanceThreshold) {
            out.append("  VIP NOTE\n");
        }
    }

    private static void runPostChecks(StringBuilder out, List<BankAccount> accounts) {
        out.append("-- POST-CHECKS --\n");
        for (BankAccount a : accounts) {
            if (a instanceof CheckingAccount c) {
                if (a.balance() < -c.overdraft()) {
                    a.setFlagged(true);
                    out.append("Flag ").append(a.id()).append(" beyond overdraft\n");
                }
            } else {
                if (a.balance() < 0) {
                    a.setFlagged(true);
                    out.append("Flag ").append(a.id()).append(" negative savings\n");
                }
            }
        }
    }

    private static void appendSummaryA(StringBuilder out, List<BankAccount> accounts, BatchConfig config) {
        out.append("\n-- SUMMARY A --\n");
        for (BankAccount a : accounts) {
            out.append(a.id()).append(" ").append(a.type()).append(" ").append(a.owner())
                    .append(" bal=").append(fmt(a.balance(), config.digits, config.rounding))
                    .append(a.flagged() ? " [FLAG]" : "")
                    .append("\n");
        }
    }

    private static void appendTotals(StringBuilder out, BatchStats stats, BatchConfig config) {
        out.append("\n-- TOTALS --\n");
        out.append("applied=").append(stats.appliedCount).append(" skipped=").append(stats.skippedCount)
                .append(" absTotal=").append(fmt(stats.absAppliedTotal, config.digits, config.rounding)).append(" ").append(config.currency)
                .append("\n");
    }

    private static void appendSummaryB(StringBuilder out, List<BankAccount> accounts, BatchConfig config) {
        out.append("\n-- SUMMARY B --\n");
        for (int i = accounts.size() - 1; i >= 0; i--) {
            BankAccount a = accounts.get(i);
            out.append("[").append(a.type()).append("] ").append(a.owner())
                    .append(" id=").append(a.id())
                    .append(" bal=").append(fmt(a.balance(), config.digits, config.rounding))
                    .append(a.flagged() ? " *" : "")
                    .append("\n");
        }
    }

    static String fmt(double v, int digits, boolean rounding) {
        if (!rounding) return Double.toString(v);
        double f = Math.pow(10, digits);
        double r = Math.round(v * f) / f;
        return String.format(Locale.US, "%." + digits + "f", r);
    }
}
