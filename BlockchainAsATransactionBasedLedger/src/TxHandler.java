import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TxHandler {
    private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
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
        
        double inputSum = 0;
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
            inputSum += inputAsPrevTransactionOutput.value;
        }
        
        double outputSum = 0;
        for (Transaction.Output output : tx.getOutputs()) {
            if (output.value < 0) {
                return false;
            }
            
            outputSum += output.value;
        }
        
        return (inputSum >= outputSum);
    }


    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        List<Transaction> result = new ArrayList<>();

        boolean atleast1Considered;
        do {
            atleast1Considered = false;
            for (Transaction txn : possibleTxs) {
                if (!result.contains(txn) && isValidTx(txn)) {
                    result.add(txn);
                    updateUtxoPool(txn);
                    atleast1Considered = true;
                }
            }
        } while (atleast1Considered);

        return result.toArray(new Transaction[0]);
    }

    private void updateUtxoPool (Transaction txn) {
        for (Transaction.Input input : txn.getInputs()) {
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            utxoPool.removeUTXO(utxo);
        }

        List<Transaction.Output> outputs = txn.getOutputs();
        for (int i = 0; i < outputs.size(); i++) {
            UTXO utxo = new UTXO(txn.getHash(), i);
            utxoPool.addUTXO(utxo, outputs.get(i));
        }
    }


//    /**
//     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
//     * transaction for correctness, returning a mutually valid array of accepted transactions, and
//     * updating the current UTXO pool as appropriate.
//     */
//    public Transaction[] handleTxs(Transaction[] possibleTxs) {
//        // IMPLEMENT THIS
//        int n = possibleTxs.length;
//        boolean[] validTxns = new boolean[n];
//
//        for (int i = 0; i < n; i++) {
//            validTxns[i] = isValidTx(possibleTxs[i]);
//        }
//
//        List<Integer> mutuallyExclusiveValidTransactions = findMutuallyExclusiveTransactions(0, validTxns, new HashSet<UTXO>(), possibleTxs);
//
//        Transaction[] result = new Transaction[mutuallyExclusiveValidTransactions.size()];
//        int i = 0;
//        for (int txnIndex : mutuallyExclusiveValidTransactions) {
//            result[result.length - i - 1] = possibleTxs[txnIndex];
//        }
//
//        return result;
//    }
//
//
//    private List<Integer> findMutuallyExclusiveTransactions (int i, boolean[] isValid, Set<UTXO> usedTxns, Transaction[] possibleTxs){
//        int n = possibleTxs.length;
//        List<Integer> result = new ArrayList<>();
//
//        if (i == n) {
//            return result;
//        }
//        else if (!isValid[i] || !areMutuallyExclusive(possibleTxs[i].getInputs(), usedTxns)) {
//            result = findMutuallyExclusiveTransactions(i + 1, isValid, usedTxns, possibleTxs);
//        }
//        else {
//            List<Integer> withoutCurrentTransaction = findMutuallyExclusiveTransactions(i + 1, isValid, usedTxns, possibleTxs);
//
//            addAllInputsToSet(possibleTxs[i].getInputs(), usedTxns);
//            List<Integer> withCurrentTransaction = findMutuallyExclusiveTransactions(i + 1, isValid, usedTxns, possibleTxs);
//            withCurrentTransaction.add(i);
//            removeAllInputsFromSet(possibleTxs[i].getInputs(), usedTxns);
//
//            result = (withCurrentTransaction.size() > withoutCurrentTransaction.size()) ? withCurrentTransaction : withoutCurrentTransaction;
//        }
//
//        return result;
//    }
//
//    private void addAllInputsToSet (List<Transaction.Input> inputs, Set<UTXO> set) {
//        for (Transaction.Input input : inputs) {
//            set.add(new UTXO(input.prevTxHash, input.outputIndex));
//        }
//    }
//
//    private void removeAllInputsFromSet (List<Transaction.Input> inputs, Set<UTXO> set) {
//        for (Transaction.Input input : inputs) {
//            set.remove(new UTXO(input.prevTxHash, input.outputIndex));
//        }
//    }
//
//    private boolean areMutuallyExclusive (List<Transaction.Input> inputs, Set<UTXO> set2) {
//        for (Transaction.Input input : inputs) {
//            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
//
//            if (set2.contains(utxo)) {
//                return false;
//            }
//        }
//
//        return true;
//    }
}
