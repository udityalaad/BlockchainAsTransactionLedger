// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory 
// as it would cause a memory overflow.

import java.util.*;

public class BlockChain {
    public class BlockData {
        public UTXOPool UtxoPool;
        public int Height;

        public BlockData (UTXOPool utxoPool, int height) {
            this.UtxoPool = utxoPool;
            this.Height = height;
        }
    }

    public static final int CUT_OFF_AGE = 10;

    private int maxBlockHeight;
    private Block maxHeightBlock;
    private final Map<ByteArrayWrapper, BlockData> treeData;
    private final Map<Integer, Set<ByteArrayWrapper>> nodesAtHeight;

    private final TransactionPool transactionPool;

    /**
     * create an empty block chain with just a genesis block. Assume {@code genesisBlock} is a valid
     * block
     */
    public BlockChain(Block genesisBlock) {
        UTXOPool utxoPool = new UTXOPool();
        for (Transaction txn : genesisBlock.getTransactions()) {
            addOutputsToUtxoPool(txn, utxoPool);
        }
        updateUtxoPool(genesisBlock.getCoinbase(), utxoPool);

        ByteArrayWrapper blockId = new ByteArrayWrapper(genesisBlock.getHash());

        this.maxBlockHeight = 0;
        this.maxHeightBlock = genesisBlock;

        this.treeData = new HashMap<>();
        treeData.put(blockId, new BlockData(utxoPool, 0));

        this.nodesAtHeight = new HashMap<>();
        this.nodesAtHeight.computeIfAbsent(0, (k) -> new HashSet<>()).add(blockId);

        this.transactionPool = new TransactionPool();
    }

    private void addOutputsToUtxoPool (Transaction txn, UTXOPool utxoPool) {
        List<Transaction.Output> outputs = txn.getOutputs();
        for (int i = 0; i < outputs.size(); i++) {
            utxoPool.addUTXO(new UTXO(txn.getHash(), i), outputs.get(i));
        }
    }

    /** Get the maximum height block */
    public Block getMaxHeightBlock() {
        return this.maxHeightBlock;
    }

    /** Get the UTXOPool for mining a new block on top of max height block */
    public UTXOPool getMaxHeightUTXOPool() {
        ByteArrayWrapper maxHeightBlockId = new ByteArrayWrapper(maxHeightBlock.getHash());
        UTXOPool maxHeightBlockUtxoPool = treeData.get(maxHeightBlockId).UtxoPool;
        return new UTXOPool(maxHeightBlockUtxoPool);
    }

    /** Get the transaction pool to mine a new block */
    public TransactionPool getTransactionPool() {
        return new TransactionPool(this.transactionPool);
    }

    /**
     * Add {@code block} to the block chain if it is valid. For validity, all transactions should be
     * valid and block should be at {@code height > (maxHeight - CUT_OFF_AGE)}.
     * 
     * <p>
     * For example, you can try creating a new block over the genesis block (block height 2) if the
     * block chain height is {@code <=
     * CUT_OFF_AGE + 1}. As soon as {@code height > CUT_OFF_AGE + 1}, you cannot create a new block
     * at height 2.
     * 
     * @return true if block is successfully added
     */
    public boolean addBlock(Block block) {
        if (block.getPrevBlockHash() == null) {
            return false;
        }

        ByteArrayWrapper prevBlockId = new ByteArrayWrapper(block.getPrevBlockHash());
        if (!this.treeData.containsKey(prevBlockId)) {
            return false;
        }

        BlockData prevBlockData = this.treeData.get(prevBlockId);
        if (prevBlockData.Height < maxBlockHeight - CUT_OFF_AGE) {
            return false;
        }

        // Check block validity
        Transaction[] possibleTransactions = block.getTransactions().toArray(new Transaction[0]);

        UTXOPool updatedUtxoPool = new UTXOPool(prevBlockData.UtxoPool);
        Transaction[] validTransactions = (new TxHandler(updatedUtxoPool)).handleTxs(possibleTransactions);
        if (validTransactions.length < possibleTransactions.length) {
            return false;
        }

        // Updates
        updateUtxoPool(block.getCoinbase(), updatedUtxoPool);

        BlockData newBlockData = new BlockData(updatedUtxoPool, prevBlockData.Height + 1);
        ByteArrayWrapper blockId = new ByteArrayWrapper(block.getHash());
        this.treeData.put(blockId, newBlockData);
        this.nodesAtHeight.computeIfAbsent(newBlockData.Height, (k) -> new HashSet<>()).add(blockId);

        if (newBlockData.Height > this.maxBlockHeight) {
            this.maxBlockHeight = newBlockData.Height;
            this.maxHeightBlock = block;

            if (this.nodesAtHeight.containsKey(this.maxBlockHeight - CUT_OFF_AGE - 40)) {
                for (ByteArrayWrapper blockIdentifier : this.nodesAtHeight.get(this.maxBlockHeight)) {
                    this.treeData.remove(blockIdentifier);
                }

                this.nodesAtHeight.remove(this.maxBlockHeight);
            }
        }

        for (Transaction txn : block.getTransactions()) {
            this.transactionPool.removeTransaction(txn.getHash());
        }

        return true;
    }

    private void updateUtxoPool (Transaction txn, UTXOPool utxoPool) {
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

    private boolean isBlockValid (Block block) {
        for (Transaction txn : block.getTransactions()) {
            if (this.transactionPool.getTransaction(txn.getHash()) == null) {
                return false;
            }
        }



        return true;
    }

    /** Add a transaction to the transaction pool */
    public void addTransaction(Transaction tx) {
        // IMPLEMENT THIS
        this.transactionPool.addTransaction(tx);
    }
}