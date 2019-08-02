package org.spring.openapi.schema.generator.model;

import org.spring.openapi.schema.generator.base.Entity;

public class Customer extends Entity {

    private boolean vip;
    private Product topCustomerProduct;

    public boolean isVip() {
        return vip;
    }

    public void setVip(boolean vip) {
        this.vip = vip;
    }

    public Product getTopCustomerProduct() {
        return topCustomerProduct;
    }

    public void setTopCustomerProduct(Product topCustomerProduct) {
        this.topCustomerProduct = topCustomerProduct;
    }
}
