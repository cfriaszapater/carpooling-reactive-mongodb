package com.cabify.carpooling.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

@RestController
public class CarPoolingController {
	@GetMapping("/status")
	public Mono<String> status() {
		return Mono.just("ok");
	}

}
