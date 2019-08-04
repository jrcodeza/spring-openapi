package org.spring.openapi.schema.generator.plugin.model;

import javax.validation.constraints.NotNull;

public class Entity {

    @NotNull
    private String id;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
