/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

import java.util.List;

/**
 *
 * @author Uditya
 */
public class TransactionsFeePair {
    public List<Integer> txnIndices;
    public double fee;
    
    public TransactionsFeePair (List<Integer> txnIndices, double fee) {
        this.txnIndices = txnIndices;
        this.fee = fee;
    }
}
