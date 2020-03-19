package com.github.jrcodeza.schema.v2.generator.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.github.jrcodeza.OpenApiIgnore;
import com.github.jrcodeza.schema.v2.generator.domain.dummy.ValidationDummy;

@RestController
@RequestMapping("/toBeIgnored")
@OpenApiIgnore
public class ControllerToBeIgnored {

	@GetMapping
	public ValidationDummy getIgnoredController(@RequestParam String someParam) {
		return null;
	}

}
