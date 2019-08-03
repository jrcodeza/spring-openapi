package org.spring.openapi.schema.generator.model;

import javax.validation.constraints.NotNull;

public class Laptop extends Product {

    @NotNull
    private String model;

    @NotNull
    private Boolean hasWifi;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Boolean getHasWifi() {
        return hasWifi;
    }

    public void setHasWifi(Boolean hasWifi) {
        this.hasWifi = hasWifi;
    }
}
