import java.util.*;
import static java.util.stream.Collectors.toSet;

/* CompliantNode refers to a node that follows the rules (not malicious) */
public class CompliantNode implements Node {

    // Simulation params (not strictly required by logic, but kept for parity with spec)
    private final double p_graph;
    private final double p_malicious;
    private final double p_txDistribution;
    private final int numRounds;

    // Graph / trust state
    private boolean[] followees;
    private boolean[] blacklisted;
    private int[] silenceCount;

    // Round tracking
    private int currentRound = 0;

    // Working sets
    private final Set<Transaction> toBroadcast = new HashSet<>(); // what we'll send next round
    private final Set<Transaction> accepted    = new HashSet<>(); // transactions we believe reached consensus

    // Tuning (kept conservative and simple)
    private static final int    SILENCE_BLACKLIST_THRESHOLD = 2;  // require repeated silence before blacklisting
    private static final double ACCEPT_FRACTION             = 0.30; // >=30% of trusted followees in the round
    private static final boolean PROPAGATE_WEAK_SIGNALS     = true; // forward any seen tx to aid convergence

    public CompliantNode(double p_graph, double p_malicious, double p_txDistribution, int numRounds) {
        this.p_graph = p_graph;
        this.p_malicious = p_malicious;
        this.p_txDistribution = p_txDistribution;
        this.numRounds = numRounds;
    }

    @Override
    public void setFollowees(boolean[] followees) {
        this.followees = followees;
        this.blacklisted = new boolean[followees.length];
        this.silenceCount = new int[followees.length];
        // no-op init; actual blacklisting is gradual based on repeated silence
    }

    @Override
    public void setPendingTransaction(Set<Transaction> pendingTransactions) {
        if (pendingTransactions == null) pendingTransactions = Collections.emptySet();
        // Seed both our "accepted" and "toBroadcast" with the initial txs we start with
        this.accepted.addAll(pendingTransactions);
        this.toBroadcast.addAll(pendingTransactions);
    }

    @Override
    public Set<Transaction> sendToFollowers() {
        // On the final round, return the transactions upon which consensus has been reached.
        // (Spec note: after the final round, behavior changes.)
        if (currentRound >= numRounds - 1) {
            return new HashSet<>(accepted);
        }
        // During normal rounds, send only new info to reduce spam; resend can be enabled if desired.
        Set<Transaction> out = new HashSet<>(toBroadcast);
        // Clear what we've sent; newly learned txs will be added by receiveFromFollowees().
        toBroadcast.clear();
        currentRound++;
        return out;
    }

    @Override
    public void receiveFromFollowees(Set<Candidate> candidates) {
        if (candidates == null) candidates = Collections.emptySet();

        // Track who actually sent something this round
        Set<Integer> roundSenders = candidates.stream().map(c -> c.sender).collect(toSet());

        // Update silence counts & blacklist only on repeated silence (avoid over-aggressive pruning)
        for (int i = 0; i < followees.length; i++) {
            if (!followees[i] || blacklisted[i]) continue;
            if (!roundSenders.contains(i)) {
                if (++silenceCount[i] >= SILENCE_BLACKLIST_THRESHOLD) {
                    blacklisted[i] = true;
                }
            } else {
                silenceCount[i] = 0; // reset if they spoke this round
            }
        }

        // Count proposals per tx from currently trusted senders
        Map<Transaction, Integer> votes = new HashMap<>();
        for (Candidate c : candidates) {
            int s = c.sender;
            if (s >= 0 && s < followees.length && followees[s] && !blacklisted[s]) {
                votes.merge(c.tx, 1, Integer::sum);
            }
        }

        int trustedFolloweesThisRound = 0;
        for (int i = 0; i < followees.length; i++) {
            if (followees[i] && !blacklisted[i]) trustedFolloweesThisRound++;
        }
        // Avoid divide-by-zero; if we trust nobody this round, just forward weak signals (if enabled)
        int acceptThreshold = (trustedFolloweesThisRound == 0)
                ? Integer.MAX_VALUE
                : Math.max(1, (int)Math.ceil(ACCEPT_FRACTION * trustedFolloweesThisRound));

        // Accept strong signals; optionally propagate weak signals for liveness
        for (Map.Entry<Transaction, Integer> e : votes.entrySet()) {
            Transaction tx = e.getKey();
            int count = e.getValue();

            if (count >= acceptThreshold) {
                if (accepted.add(tx)) {
                    // new consensus-worthy tx -> also broadcast next round so others can learn it
                    toBroadcast.add(tx);
                }
            } else if (PROPAGATE_WEAK_SIGNALS) {
                // Forward anything seen from at least one trusted followee to improve convergence
                if (toBroadcast.add(tx)) {
                    // don't mark as accepted yet; might reach threshold in later rounds
                }
            }
        }
    }
}