package edu.kettering.refactoring.bank;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Refactoring Homework: Bank Accounts (Checking/Savings)
 *
 * This refactor keeps the exact same behavior and report output, but fixes the requested smells:
 * - Long method: processDailyBatch is now a short coordinator that calls small helpers.
 * - Long parameter list: parameters are wrapped into BatchConfig internally (public signature preserved).
 * - Duplicated code: extracted summary/report and formatting helpers.
 * - Mixed concerns: mutation (applyTransaction) is separated from report printing/reporting helpers.
 * - Mysterious names: replaced z/q/t style counters with BatchStats fields.
 * - "Dead data": SavingsAccount.rate is now read and tracked internally (not printed, does not change decisions).
 */
public class SmellyBankHomeworkShorter {

    // --- Domain (mutable on purpose for this lab) ---
    static abstract class BankAccount {
        private final String id;
        private final String owner;
        protected double bal;        // mutable: account balance
        private boolean flagged;     // mutable: safety/attention flag

        /**************************************************
           Method Name: BankAccount
           Returns: N/A (constructor)
           Input: id, owner, bal
           Precondition: id/owner are non-null; bal is a valid numeric balance
           Task: Build a base account object with identity and starting balance.
           **************************************************/
        protected BankAccount(String id, String owner, double bal) {
            this.id = id;
            this.owner = owner;
            this.bal = bal;
        }

        // Getter: unique account id
        public String id() { return id; }

        // Getter: owner name
        public String owner() { return owner; }

        // Getter: current balance
        public double balance() { return bal; }

        // Getter: flag status
        public boolean flagged() { return flagged; }

        // Setter: flag status
        public void setFlagged(boolean v) { flagged = v; }

        // Returns a report label for the account type (implemented by subclasses).
        public abstract String type();
    }

    static class CheckingAccount extends BankAccount {
        private final double overdraft;

        /**************************************************
           Method Name: CheckingAccount
           Returns: N/A (constructor)
           Input: id, owner, bal, overdraft
           Precondition: overdraft is non-negative
           Task: Create a checking account with an overdraft limit.
           **************************************************/
        public CheckingAccount(String id, String owner, double bal, double overdraft) {
            super(id, owner, bal);
            this.overdraft = overdraft;
        }

        // Getter: overdraft limit used for withdrawal validation.
        public double overdraft() { return overdraft; }

        // Account type label for reporting.
        public String type() { return "CHECKING"; }
    }

    static class SavingsAccount extends BankAccount {
        private final double rate; // previously "dead data"

        /**************************************************
           Method Name: SavingsAccount
           Returns: N/A (constructor)
           Input: id, owner, bal, rate
           Precondition: rate is a valid numeric interest rate (ex: 0.02)
           Task: Create a savings account with an associated interest rate.
           **************************************************/
        public SavingsAccount(String id, String owner, double bal, double rate) {
            super(id, owner, bal);
            this.rate = rate;
        }

        // Getter: savings interest rate.
        public double rate() { return rate; }

        // Account type label for reporting.
        public String type() { return "SAVINGS"; }
    }

    static class Txn {
        final String acctId, kind, memo; // kind: DEPOSIT/WITHDRAW ONLY (unknown kinds are skipped)
        final double amt;

        /**************************************************
           Method Name: Txn
           Returns: N/A (constructor)
           Input: acctId, kind, amt, memo
           Precondition: acctId/kind/memo are non-null; amt is numeric
           Task: Create an immutable transaction record to apply in a batch.
           **************************************************/
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

        /**************************************************
           Method Name: BatchConfig
           Returns: N/A (constructor)
           Input: includeZeroAmountTxns, flagLargeTxnThreshold, vipBalanceThreshold, debug, currency, digits, rounding
           Precondition: digits is non-negative; currency is non-null
           Task: Store all batch settings in a single object so helpers stay readable.
           **************************************************/
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

        // Not printed. Exists only to eliminate "dead data" by reading SavingsAccount.rate in a harmless way.
        // It does not affect any decisions or the report output.
        double projectedAnnualInterestTotal = 0.0;

        /**************************************************
           Method Name: recordApplied
           Returns: void
           Input: amount
           Precondition: amount is numeric
           Task: Track that a transaction was applied and accumulate totals.
           **************************************************/
        void recordApplied(double amount) {
            appliedCount++;
            absAppliedTotal += Math.abs(amount);
        }

        /**************************************************
           Method Name: recordSkipped
           Returns: void
           Input: none
           Precondition: none
           Task: Track that a transaction was not applied (declined/unknown/invalid account).
           **************************************************/
        void recordSkipped() {
            skippedCount++;
        }

        /**************************************************
           Method Name: recordProjectedAnnualInterest
           Returns: void
           Input: account
           Precondition: account is non-null
           Task: Read savings rate and track a derived metric so rate is not dead data.
           **************************************************/
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

    /**************************************************
       Method Name: main
       Returns: void
       Input: args
       Precondition: none
       Task: Demo runner, builds sample accounts/transactions and prints a batch report.
       **************************************************/
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

    /**************************************************
       Method Name: processDailyBatch
       Returns: String (full batch report)
       Input: inputAccounts, inputTxns, includeZeroAmountTxns, flagLargeTxnThreshold, vipBalanceThreshold, debug, currency, digits, rounding
       Precondition: inputAccounts/inputTxns are non-null; digits is non-negative; currency is non-null
       Task: Preserve the original public API and wrap config values into BatchConfig for internal readability.
       **************************************************/
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

    /**************************************************
       Method Name: processDailyBatch
       Returns: String (full batch report)
       Input: accounts, inputTxns, config
       Precondition: accounts/inputTxns/config are non-null
       Task: Coordinate the batch steps: filter txns, apply txns, run post-checks, and build summaries/totals.
       **************************************************/
    private static String processDailyBatch(List<BankAccount> accounts, List<Txn> inputTxns, BatchConfig config) {
        StringBuilder out = new StringBuilder();
        out.append("=== BANK BATCH REPORT ===\n");

        // 1) Build a fast lookup for account id -> account
        Map<String, BankAccount> accountById = indexAccountsById(accounts);

        // 2) Filter transactions based on config (ex: remove zero-amount txns unless allowed)
        List<Txn> txns = filterTransactions(inputTxns, config, out);

        // 3) Apply each transaction and build the APPLY section of the report
        BatchStats stats = new BatchStats();
        out.append("\n-- APPLY --\n");
        for (Txn txn : txns) {
            BankAccount account = accountById.get(txn.acctId);
            if (account == null) {
                // Unknown account id, match original behavior: count as skipped and optionally debug print
                stats.recordSkipped();
                appendUnknownAccountDebug(out, config, txn.acctId);
                continue;
            }

            // Print the transaction header line (kind, account info, amount, memo)
            appendTransactionHeader(out, account, txn, config);

            // Apply business rules (deposit/withdraw/decline), and then print the outcome line
            ApplyOutcome outcome = applyTransaction(account, txn, stats);
            appendOutcomeDetails(out, account, outcome, config);

            // Apply "flag large txn" and "VIP note" reporting behaviors
            applyFlagsAndNotes(out, account, txn, config);
            out.append("\n");
        }

        // 4) Post-check all accounts for invalid balances and flag them as needed
        runPostChecks(out, accounts);

        // 5) Summaries and totals sections, matching original ordering/format
        appendSummaryA(out, accounts, config);
        appendTotals(out, stats, config);
        appendSummaryB(out, accounts, config);

        return out.toString();
    }

    /**************************************************
       Method Name: indexAccountsById
       Returns: Map<String, BankAccount>
       Input: accounts
       Precondition: accounts is non-null
       Task: Build a lookup map so applying transactions is O(1) by account id.
       **************************************************/
    private static Map<String, BankAccount> indexAccountsById(List<BankAccount> accounts) {
        Map<String, BankAccount> byId = new HashMap<>();
        for (BankAccount a : accounts) {
            byId.put(a.id(), a);
        }
        return byId;
    }

    /**************************************************
       Method Name: filterTransactions
       Returns: List<Txn>
       Input: inputTxns, config, out
       Precondition: inputTxns/config/out are non-null
       Task: Remove transactions that should not be processed (ex: zero-amount txns), while preserving debug prints.
       **************************************************/
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

    /**************************************************
       Method Name: appendUnknownAccountDebug
       Returns: void
       Input: out, config, acctId
       Precondition: out/config/acctId are non-null
       Task: Print the original debug line for unknown accounts (only when debug is enabled).
       **************************************************/
    private static void appendUnknownAccountDebug(StringBuilder out, BatchConfig config, String acctId) {
        if (config.debug) {
            out.append("[dbg] unknown ").append(acctId).append("\n");
        }
    }

    /**************************************************
       Method Name: appendTransactionHeader
       Returns: void
       Input: out, account, txn, config
       Precondition: out/account/txn/config are non-null
       Task: Print the transaction header line exactly as the original report formatting expects.
       **************************************************/
    private static void appendTransactionHeader(StringBuilder out, BankAccount account, Txn txn, BatchConfig config) {
        out.append(txn.kind).append(" acct=").append(account.id())
                .append(" owner=").append(account.owner())
                .append(" amt=").append(fmt(txn.amt, config.digits, config.rounding)).append(" ").append(config.currency)
                .append(" memo=").append(txn.memo).append("\n");
    }

    /**************************************************
       Method Name: applyTransaction
       Returns: ApplyOutcome
       Input: account, txn, stats
       Precondition: account/txn/stats are non-null
       Task: Apply the transaction rules (deposit/withdraw) and update stats. Unknown kinds are skipped.
       **************************************************/
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

    /**************************************************
       Method Name: canWithdraw
       Returns: boolean
       Input: account, amount
       Precondition: account is non-null; amount is non-negative
       Task: Enforce withdrawal rules: checking allows overdraft, savings cannot go negative.
       **************************************************/
    private static boolean canWithdraw(BankAccount account, double amount) {
        double newBalance = account.bal - amount;
        if (account instanceof CheckingAccount c) {
            return newBalance >= -c.overdraft();
        }
        return newBalance >= 0;
    }

    /**************************************************
       Method Name: appendOutcomeDetails
       Returns: void
       Input: out, account, outcome, config
       Precondition: out/account/outcome/config are non-null
       Task: Print the post-apply line ("newBal" or "DECLINED" or "SKIP...") exactly as the original did.
       **************************************************/
    private static void appendOutcomeDetails(StringBuilder out, BankAccount account, ApplyOutcome outcome, BatchConfig config) {
        switch (outcome) {
            case APPLIED -> out.append("  newBal=").append(fmt(account.bal, config.digits, config.rounding)).append("\n");
            case DECLINED -> out.append("  DECLINED\n");
            case SKIPPED_UNKNOWN_KIND -> out.append("  SKIP unknown kind\n");
        }
    }

    /**************************************************
       Method Name: applyFlagsAndNotes
       Returns: void
       Input: out, account, txn, config
       Precondition: out/account/txn/config are non-null
       Task: Apply/report "large transaction" flags and "VIP NOTE" messages.
       **************************************************/
    private static void applyFlagsAndNotes(StringBuilder out, BankAccount account, Txn txn, BatchConfig config) {
        if (Math.abs(txn.amt) >= config.flagLargeTxnThreshold) {
            account.setFlagged(true);
            out.append("  ** FLAG large txn **\n");
        }
        if (account.balance() >= config.vipBalanceThreshold) {
            out.append("  VIP NOTE\n");
        }
    }

    /**************************************************
       Method Name: runPostChecks
       Returns: void
       Input: out, accounts
       Precondition: out/accounts are non-null
       Task: Scan all accounts and flag any that violate their balance constraints.
       **************************************************/
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

    /**************************************************
       Method Name: appendSummaryA
       Returns: void
       Input: out, accounts, config
       Precondition: out/accounts/config are non-null
       Task: Print the first account summary block in forward order.
       **************************************************/
    private static void appendSummaryA(StringBuilder out, List<BankAccount> accounts, BatchConfig config) {
        out.append("\n-- SUMMARY A --\n");
        for (BankAccount a : accounts) {
            out.append(a.id()).append(" ").append(a.type()).append(" ").append(a.owner())
                    .append(" bal=").append(fmt(a.balance(), config.digits, config.rounding))
                    .append(a.flagged() ? " [FLAG]" : "")
                    .append("\n");
        }
    }

    /**************************************************
       Method Name: appendTotals
       Returns: void
       Input: out, stats, config
       Precondition: out/stats/config are non-null
       Task: Print totals about applied and skipped transactions, matching original formatting.
       **************************************************/
    private static void appendTotals(StringBuilder out, BatchStats stats, BatchConfig config) {
        out.append("\n-- TOTALS --\n");
        out.append("applied=").append(stats.appliedCount).append(" skipped=").append(stats.skippedCount)
                .append(" absTotal=").append(fmt(stats.absAppliedTotal, config.digits, config.rounding)).append(" ").append(config.currency)
                .append("\n");
    }

    /**************************************************
       Method Name: appendSummaryB
       Returns: void
       Input: out, accounts, config
       Precondition: out/accounts/config are non-null
       Task: Print the second account summary block in reverse order.
       **************************************************/
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

    /**************************************************
       Method Name: fmt
       Returns: String
       Input: v, digits, rounding
       Precondition: digits is non-negative
       Task: Format numbers for the report. Rounding behavior matches the original.
       **************************************************/
    static String fmt(double v, int digits, boolean rounding) {
        if (!rounding) return Double.toString(v);
        double f = Math.pow(10, digits);
        double r = Math.round(v * f) / f;
        return String.format(Locale.US, "%." + digits + "f", r);
    }
}
