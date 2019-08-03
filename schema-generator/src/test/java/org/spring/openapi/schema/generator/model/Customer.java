package org.spring.openapi.schema.generator.model;

import io.swagger.v3.oas.annotations.media.Schema;

public class Customer extends Entity {

    private boolean vip;

    @Schema(description = "Testing description")
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
