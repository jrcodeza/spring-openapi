package com.github.jrcodeza.schema.generator.model;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;

import com.github.jrcodeza.OpenApiExample;
import com.github.jrcodeza.OpenApiExamples;

@OpenApiExamples(value = {
        @OpenApiExample(name = "CarExampleName_1", value = "carExampleValue_1"),
        @OpenApiExample(name = "CarExampleName_2", value = "carExampleValue_2")
})
public class Car extends Product {

    public static final String SHOULD_BE_IGNORED_BECAUSE_STATIC = "TestValue";

    @Min(0)
    @Max(1000)
    private Integer torque;

    private Integer maxSpeed;

    @Size(min = 2, max = 30)
    @OpenApiExample(value = "field example")
    private String model;

    private CarType carType;

    public Integer getTorque() {
        return torque;
    }

    public void setTorque(Integer torque) {
        this.torque = torque;
    }

    public Integer getMaxSpeed() {
        return maxSpeed;
    }

    public void setMaxSpeed(Integer maxSpeed) {
        this.maxSpeed = maxSpeed;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public CarType getCarType() {
        return carType;
    }

    public void setCarType(CarType carType) {
        this.carType = carType;
    }
}
