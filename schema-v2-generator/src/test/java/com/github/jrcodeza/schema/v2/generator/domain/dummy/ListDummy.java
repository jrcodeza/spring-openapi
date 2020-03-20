package com.github.jrcodeza.schema.v2.generator.domain.dummy;

import javax.validation.constraints.Size;
import java.util.List;

import com.github.jrcodeza.schema.v2.generator.domain.Car;
import com.github.jrcodeza.schema.v2.generator.domain.CarType;
import com.github.jrcodeza.schema.v2.generator.domain.Product;

public class ListDummy {

	@Size(min = 2, max = 6)
	private List<Product> products;
	private List<Integer> integers;
	private List<Car> cars;
	private List<CarType> enums;

	public List<Product> getProducts() {
		return products;
	}

	public void setProducts(List<Product> products) {
		this.products = products;
	}

	public List<Integer> getIntegers() {
		return integers;
	}

	public void setIntegers(List<Integer> integers) {
		this.integers = integers;
	}

	public List<Car> getCars() {
		return cars;
	}

	public void setCars(List<Car> cars) {
		this.cars = cars;
	}

	public List<CarType> getEnums() {
		return enums;
	}

	public void setEnums(List<CarType> enums) {
		this.enums = enums;
	}
}
