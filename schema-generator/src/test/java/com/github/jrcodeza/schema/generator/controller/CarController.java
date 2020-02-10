package com.github.jrcodeza.schema.generator.controller;

import java.util.List;

import com.github.jrcodeza.schema.generator.domain.Car;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/cars")
public class CarController {

	@PostMapping
	public Car createCar(@RequestHeader("source") String source, @RequestBody Car car) {
		return null;
	}

	@GetMapping
	public List<Car> getCars(@RequestParam(required = false) String model, @RequestParam(required = false) Integer torque) {
		return null;
	}

	@GetMapping("/{carId}")
	public Car getCar(@PathVariable("carId") String carId) {
		return null;
	}

	@PostMapping("/{carId}/photos")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void uploadCarPhoto(@PathVariable String carId, @RequestBody MultipartFile multipartFile) {
		// do nothing
	}

	@RequestMapping(path = "/{carId}/documents", consumes = "multipart/form-data", produces = "application/json", method = RequestMethod.POST)
	public Car uploadCarDocuments(@RequestHeader("source") String source,
								  @PathVariable("carId") String carId,
								  @RequestParam(name = "documentFile") MultipartFile documentFile,
								  @RequestParam(name = "type") String type) {
		return null;
	}

	public void noOperationMethod() {
		// do nothing
	}

}
