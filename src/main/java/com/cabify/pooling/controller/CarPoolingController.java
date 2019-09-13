package com.cabify.pooling.controller;

import java.util.List;

import javax.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.cabify.pooling.dto.CarDTO;
import com.cabify.pooling.dto.GroupOfPeopleDTO;
import com.cabify.pooling.exception.GroupAlreadyExistsException;
import com.cabify.pooling.service.CarPoolingService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class CarPoolingController {

	private final CarPoolingService carPoolingService;

	@GetMapping("/status")
	public Mono<String> status() {
		return Mono.just("ok");
	}

	@PutMapping(path = "/cars", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Void> putCars(@RequestBody @Valid List<CarDTO> cars) {
		carPoolingService.createCars(cars).subscribe();
		return ResponseEntity.ok().build();
	}

	@PostMapping(path = "/journey", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Void> postJourney(@RequestBody @Valid GroupOfPeopleDTO group) throws GroupAlreadyExistsException {
		carPoolingService.journey(group);
		return ResponseEntity.ok().build();
	}

}
