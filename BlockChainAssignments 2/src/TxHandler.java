import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class TxHandler {
    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    private UTXOPool mPool;
    public TxHandler(UTXOPool utxoPool) {
        mPool = new UTXOPool(utxoPool);
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
        double mGrossInput = 0.0;
        double mGrossOutput = 0.0;
        Set<UTXO> mAUtxoAvailable = new HashSet<UTXO>();
        for(Transaction.Input input: tx.getInputs()){
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            if(!mPool.contains(utxo))
                return false;
            Transaction.Output output = mPool.getTxOutput(utxo);
            if(output == null)
                return false;
            PublicKey key = output.address;
            if(!Crypto.verifySignature(key, tx.getRawDataToSign(tx.getInputs().indexOf(input)),input.signature))
                return false;
            if(!mAUtxoAvailable.add(utxo))
                return false;
            mGrossInput += output.value;
            }
        ArrayList<Transaction.Output> transactionOutputs = tx.getOutputs();
        for(Transaction.Output output : transactionOutputs){
            if(output.value <0)
                return false;
            mGrossOutput += output.value;
        }

        if(mGrossInput < mGrossOutput )
            return false;
        return true;

    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        ArrayList<Transaction> verifiedTransac = new ArrayList<>();
        for(Transaction transaction : possibleTxs){
            if(isValidTx(transaction)){
                verifiedTransac.add(transaction);
                updatingPool(transaction);
            }
        }
        Transaction[] result = new Transaction[verifiedTransac.size()];
        verifiedTransac.toArray(result);
        return result;
    }
    private void updatingPool(Transaction transaction){
        int index = 0;
        for(Transaction.Input input: transaction.getInputs()){
            UTXO utxo = new UTXO(input.prevTxHash,input.outputIndex);
            mPool.removeUTXO(utxo);
        }
        for (Transaction.Output output : transaction.getOutputs()){
            UTXO utxo = new UTXO(transaction.getHash(),index++);
            mPool.addUTXO(utxo,output);
        }
    }

}
