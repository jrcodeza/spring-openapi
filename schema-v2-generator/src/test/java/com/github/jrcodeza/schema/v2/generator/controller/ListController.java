package com.github.jrcodeza.schema.v2.generator.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import com.github.jrcodeza.schema.v2.generator.domain.Car;

@RestController
@RequestMapping("/lists")
public class ListController {

	@GetMapping("with-response-entity")
	public ResponseEntity<List<Car>> getCarsWithResponseEntity(@RequestParam(required = false) String model, @RequestParam(required = false) Integer torque) {
		return null;
	}

	@GetMapping("list-without-generics")
	public ResponseEntity<List> getCarsWithListWithoutGenerics(@RequestParam(required = false) String model, @RequestParam(required = false) Integer torque) {
		return null;
	}
}
