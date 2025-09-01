import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class Pair<K, V> {
    private final K first;
    private final V second;

    public Pair(K first, V second) {
        this.first = first;
        this.second = second;
    }

    public K getKey() {
        return first;
    }

    public V getValue() {
        return second;
    }

    // You might also want to override equals() and hashCode() for proper comparison
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pair<?, ?> pair = (Pair<?, ?>) o;
        return first.equals(pair.first) && second.equals(pair.second);
    }

    @Override
    public int hashCode() {
        return 31 * first.hashCode() + second.hashCode();
    }
}

public class MaxFeeTxHandler {
    private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public MaxFeeTxHandler(UTXOPool utxoPool) {
        // IMPLEMENT THIS
        this.utxoPool = utxoPool;
    }
    
    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        // IMPLEMENT THIS
        Set<UTXO> usedTransactions = new HashSet<>();
        List<Transaction.Input> inputs = tx.getInputs();
        
        for (int  i = 0; i < inputs.size(); i++) {
            Transaction.Input input = inputs.get(i);
            UTXO utxoId = new UTXO(input.prevTxHash, input.outputIndex);
            
            if (!utxoPool.contains(utxoId) ||  usedTransactions.contains(utxoId)) {
                return false;
            }
            
            Transaction.Output inputAsPrevTransactionOutput = utxoPool.getTxOutput(utxoId);
            if (!Crypto.verifySignature(inputAsPrevTransactionOutput.address, tx.getRawDataToSign(i), input.signature)) {
                return false;
            }
            
            usedTransactions.add(utxoId);
        }
        
        return findTxnFee(tx) >= 0;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        // IMPLEMENT THIS
        int n = possibleTxs.length;
        boolean[] validTxns = new boolean[n];
        
        for (int i = 0; i < n; i++) {
            validTxns[i] = isValidTx(possibleTxs[i]);
        }
        
        List<Integer> mutuallyExclusiveValidTransactions = findMutuallyExclusiveTransactions(0, validTxns, new HashSet<UTXO>(), possibleTxs).getKey();
        
        Transaction[] result = new Transaction[mutuallyExclusiveValidTransactions.size()];
        int i = 0;
        for (int txnIndex : mutuallyExclusiveValidTransactions) {
            result[result.length - i - 1] = possibleTxs[txnIndex];
        }
        
        return result;
    }
    
    private Pair<List<Integer>, Double> findMutuallyExclusiveTransactions (int i, boolean[] isValid, Set<UTXO> usedTxns, Transaction[] possibleTxs){
        int n = possibleTxs.length;
        Pair<List<Integer>, Double> result = new Pair(new ArrayList<>(), (double) 0);
        
        if (i == n) {
            return result;
        } 
        else if (!isValid[i] || !areMutuallyExclusive(possibleTxs[i].getInputs(), usedTxns)) {
            result = findMutuallyExclusiveTransactions(i + 1, isValid, usedTxns, possibleTxs);
        }
        else {
            Pair<List<Integer>, Double> withoutCurrentTransaction = findMutuallyExclusiveTransactions(i + 1, isValid, usedTxns, possibleTxs);

            addAllInputsToSet(possibleTxs[i].getInputs(), usedTxns);
            Pair<List<Integer>, Double> withCurrentTransaction = findMutuallyExclusiveTransactions(i + 1, isValid, usedTxns, possibleTxs);
            withCurrentTransaction.getKey().add(i);
            withCurrentTransaction = new Pair(withCurrentTransaction.getKey(), withCurrentTransaction.getValue() + findTxnFee(possibleTxs[i]));
            removeAllInputsFromSet(possibleTxs[i].getInputs(), usedTxns);
            
            result = (withCurrentTransaction.getValue() > withoutCurrentTransaction.getValue()) ? withCurrentTransaction : withoutCurrentTransaction;
        }
        
        return result;
    }
    
    private double findTxnFee (Transaction txn) {
        double inputSum = 0;
        for (Transaction.Input input : txn.getInputs()) {
            inputSum += utxoPool.getTxOutput(new UTXO(input.prevTxHash, input.outputIndex)).value;
        }
        
        double outputSum = 0;
        for (Transaction.Output output : txn.getOutputs()) {
            outputSum += output.value;
        }
        
        return (inputSum - outputSum);
    }
    
    private void addAllInputsToSet (List<Transaction.Input> inputs, Set<UTXO> set) {
        for (Transaction.Input input : inputs) {
            set.add(new UTXO(input.prevTxHash, input.outputIndex));
        }
    }
    
    private void removeAllInputsFromSet (List<Transaction.Input> inputs, Set<UTXO> set) {
        for (Transaction.Input input : inputs) {
            set.remove(new UTXO(input.prevTxHash, input.outputIndex));
        }
    }
    
    private boolean areMutuallyExclusive (List<Transaction.Input> inputs, Set<UTXO> set2) {
        for (Transaction.Input input : inputs) {
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            
            if (set2.contains(utxo)) {
                return false;
            }
        }
        
        return true;
    }
}
