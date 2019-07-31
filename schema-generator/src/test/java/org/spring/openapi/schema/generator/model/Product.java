package org.spring.openapi.schema.generator.model;

import org.spring.openapi.schema.generator.base.Entity;

import java.math.BigDecimal;

public class Product extends Entity {

    private BigDecimal price;
    private int amount;

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }
}
