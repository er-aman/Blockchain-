import java.security.PublicKey;
import java.util.*;

public class MaxFeeTxHandler   {
    public UTXOPool utxoPool;

    public MaxFeeTxHandler(UTXOPool utxoPool){
        this.utxoPool = utxoPool;
    }
    public boolean isValidTx(Transaction tx) {
        double mGrossInput = 0.0;
        double mGrossOutput = 0.0;
        Set<UTXO> mAUtxoAvailable = new HashSet<UTXO>();
        for(Transaction.Input input: tx.getInputs()){
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            if(!utxoPool.contains(utxo))
                return false;
            Transaction.Output output = utxoPool.getTxOutput(utxo);
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

    public class TransactionWithIncentive implements Comparable<TransactionWithIncentive>{
        public Transaction mTransaction;
        public double mIncentive;

        public TransactionWithIncentive(Transaction transaction){
            mTransaction = transaction;
            double grossInput=  0, grossOutput = 0;
            for(Transaction.Input input : mTransaction.getInputs()){
                UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
                grossInput += utxoPool.getTxOutput(utxo).value;
            }
            for(Transaction.Output output: mTransaction.getOutputs()){
                grossOutput += output.value;
            }
            mIncentive = grossInput - grossOutput;
        }


        @Override
        public int compareTo(TransactionWithIncentive o) {
            if(mIncentive< o.mIncentive){
                return -1;
            }else if(mIncentive == o.mIncentive){
                return 0;
            }else{
                return 1;
            }
        }
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        ArrayList<TransactionWithIncentive> incentiveTx = new ArrayList<>();
        int i =0;
        for(Transaction transaction : possibleTxs){
            if(isValidTx(transaction)){
                TransactionWithIncentive ti = new TransactionWithIncentive(transaction);
                incentiveTx.add(ti);
                updatingPool(transaction);
            }
        }
        Collections.sort(incentiveTx);
        i = incentiveTx.size() -1;
        for (int j =1; i>0 && j>1; i--,j-- ){
            if(incentiveTx.get(i).mIncentive != incentiveTx.get(j--).mIncentive){
                break;
            }
        }
        Transaction[] result = new Transaction[incentiveTx.size()];
        for(int k=0; k< incentiveTx.size(); k++,i++){
            result[k] = incentiveTx.get(i).mTransaction;
        }
        return result;
    }
    private void updatingPool(Transaction transaction){
        int index = 0;
        for(Transaction.Input input: transaction.getInputs()){
            UTXO utxo = new UTXO(input.prevTxHash,input.outputIndex);
            utxoPool.removeUTXO(utxo);
        }
        for (Transaction.Output output : transaction.getOutputs()){
            UTXO utxo = new UTXO(transaction.getHash(),index++);
            utxoPool.addUTXO(utxo,output);
        }
    }
}
