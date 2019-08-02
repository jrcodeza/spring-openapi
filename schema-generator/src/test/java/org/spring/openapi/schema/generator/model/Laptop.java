package org.spring.openapi.schema.generator.model;

public class Laptop extends Product {

    private String model;
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
