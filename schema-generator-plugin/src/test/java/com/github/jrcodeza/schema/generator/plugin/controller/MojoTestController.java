package com.github.jrcodeza.schema.generator.plugin.controller;

import com.github.jrcodeza.schema.generator.plugin.model.OpenApiTestModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("mojoTest")
public class MojoTestController {

	@GetMapping
	private OpenApiTestModel getMojoTest(@RequestParam String mojoTestParam) {
		return null;
	}

}
