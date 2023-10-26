package com.bankedtooltips.data;

import lombok.Value;

@Value
public class BankItem {
    int itemId;
    int quantity;

    public BankItem(int canonId, int quantity) {
        this.itemId = canonId;
        this.quantity = quantity;
    }

    public int getItemId() {
        return itemId;
    }

    public int getQuantity() {
        return quantity;
    }
}