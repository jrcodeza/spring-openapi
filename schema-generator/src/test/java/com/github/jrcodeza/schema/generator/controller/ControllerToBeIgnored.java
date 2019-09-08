package com.github.jrcodeza.schema.generator.controller;

import com.github.jrcodeza.OpenApiIgnore;
import com.github.jrcodeza.schema.generator.model.dummy.ValidationDummy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/toBeIgnored")
@OpenApiIgnore
public class ControllerToBeIgnored {

	@GetMapping
	public ValidationDummy getIgnoredController(@RequestParam String someParam) {
		return null;
	}

}
