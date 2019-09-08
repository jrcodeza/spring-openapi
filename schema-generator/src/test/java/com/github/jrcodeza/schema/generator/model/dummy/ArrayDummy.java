package com.github.jrcodeza.schema.generator.model.dummy;

import com.github.jrcodeza.schema.generator.model.Car;
import com.github.jrcodeza.schema.generator.model.Product;

public class ArrayDummy {

    private Integer[] integers;
    private Product[] products;
    private Car[] cars;
    private int[] primitiveIntegers;

    public Integer[] getIntegers() {
        return integers;
    }

    public void setIntegers(Integer[] integers) {
        this.integers = integers;
    }

    public Product[] getProducts() {
        return products;
    }

    public void setProducts(Product[] products) {
        this.products = products;
    }

    public Car[] getCars() {
        return cars;
    }

    public void setCars(Car[] cars) {
        this.cars = cars;
    }

    public int[] getPrimitiveIntegers() {
        return primitiveIntegers;
    }

    public void setPrimitiveIntegers(int[] primitiveIntegers) {
        this.primitiveIntegers = primitiveIntegers;
    }
}
