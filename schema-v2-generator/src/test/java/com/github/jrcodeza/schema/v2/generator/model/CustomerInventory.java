package com.github.jrcodeza.schema.v2.generator.model;

public class CustomerInventory {

	private Customer[] customers;

	private Object object;

	public Customer[] getCustomers() {
		return customers;
	}

	public void setCustomers(Customer[] customers) {
		this.customers = customers;
	}

	public Object getObject() {
		return object;
	}

	public void setObject(Object object) {
		this.object = object;
	}
}
