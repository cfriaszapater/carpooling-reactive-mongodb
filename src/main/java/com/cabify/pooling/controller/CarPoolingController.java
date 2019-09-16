package com.cabify.pooling.controller;

import java.util.List;

import javax.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.cabify.pooling.dto.CarDTO;
import com.cabify.pooling.dto.GroupOfPeopleDTO;
import com.cabify.pooling.dto.GroupOfPeopleForm;
import com.cabify.pooling.entity.CarEntity;
import com.cabify.pooling.exception.GroupAlreadyExistsException;
import com.cabify.pooling.service.CarPoolingService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
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
	public Flux<CarEntity> putCars(@RequestBody @Valid List<CarDTO> cars) {
		return carPoolingService.createCars(cars);
	}

	@PostMapping(path = "/journey", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Mono<CarEntity> postJourney(@RequestBody @Valid GroupOfPeopleDTO group) throws GroupAlreadyExistsException {
		return carPoolingService.journey(group);
	}

	@PostMapping(path = "/dropoff", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
	public Mono<ResponseEntity<Void>> postDropoff(@Valid GroupOfPeopleForm group) {
		Integer id = group.getID();
		
		Mono<ResponseEntity<Void>> findAndRemove = carPoolingService.findWaitingGroup(id)
				.map(g -> carPoolingService.removeWaitingGroup(id))
				.map(v -> new ResponseEntity<Void>(HttpStatus.NO_CONTENT))
				.defaultIfEmpty(new ResponseEntity<Void>(HttpStatus.NOT_FOUND));
		
		return carPoolingService.dropoff(id)
				.map(car -> new ResponseEntity<Void>(HttpStatus.OK))
				.switchIfEmpty(findAndRemove);
	}

	@PostMapping(path = "/locate", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public Mono<ResponseEntity<CarDTO>> postLocate(@Valid GroupOfPeopleForm group) {
		Integer groupId = group.getID();

		return carPoolingService.locateCarOfGroup(groupId)
				.map(car -> ResponseEntity.ok(new CarDTO(car.getId(), car.getSeatsAvailable())))
				.switchIfEmpty(carPoolingService.findWaitingGroup(groupId)
						.map(g -> new ResponseEntity<CarDTO>(HttpStatus.NO_CONTENT))
						.defaultIfEmpty(new ResponseEntity<CarDTO>(HttpStatus.NOT_FOUND)));
	}
}
