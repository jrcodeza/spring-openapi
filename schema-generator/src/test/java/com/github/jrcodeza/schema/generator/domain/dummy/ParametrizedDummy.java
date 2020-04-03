package com.github.jrcodeza.schema.generator.domain.dummy;

import java.util.List;

public class ParametrizedDummy<T> {

    private List<T> data;
    private int totalCount;

    public List<T> getData() {
        return data;
    }

    public void setData(List<T> data) {
        this.data = data;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
}
