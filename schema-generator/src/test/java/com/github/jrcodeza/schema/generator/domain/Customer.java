package com.github.jrcodeza.schema.generator.domain;

import com.github.jrcodeza.OpenApiIgnore;

import io.swagger.v3.oas.annotations.media.Schema;

public class Customer extends Entity {

    private boolean vip;

    @Schema(description = "Testing description")
    private Product topCustomerProduct;

    @OpenApiIgnore
    private String toBeIgnored;

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

    public String getToBeIgnored() {
        return toBeIgnored;
    }

    public void setToBeIgnored(String toBeIgnored) {
        this.toBeIgnored = toBeIgnored;
    }
}
