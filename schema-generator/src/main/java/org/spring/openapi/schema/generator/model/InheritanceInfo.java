package org.spring.openapi.schema.generator.model;

import org.spring.openapi.schema.generator.annotations.OpenApiIgnore;

import java.util.Map;

@OpenApiIgnore
public class InheritanceInfo {

    private String discriminatorFieldName;
    private Map<String, String> discriminatorClassMap;

    public String getDiscriminatorFieldName() {
        return discriminatorFieldName;
    }

    public void setDiscriminatorFieldName(String discriminatorName) {
        this.discriminatorFieldName = discriminatorName;
    }

    public Map<String, String> getDiscriminatorClassMap() {
        return discriminatorClassMap;
    }

    public void setDiscriminatorClassMap(Map<String, String> discriminatorClassMap) {
        this.discriminatorClassMap = discriminatorClassMap;
    }
}
