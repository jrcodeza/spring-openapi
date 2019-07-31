package org.spring.openapi.schema.generator.model;

import org.spring.openapi.schema.generator.base.Entity;

public class Customer extends Entity {

    private boolean vip;

    public boolean isVip() {
        return vip;
    }

    public void setVip(boolean vip) {
        this.vip = vip;
    }
}
