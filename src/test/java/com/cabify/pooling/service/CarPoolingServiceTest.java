package com.cabify.pooling.service;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import com.cabify.pooling.dto.CarDTO;
import com.cabify.pooling.dto.GroupOfPeopleDTO;
import com.cabify.pooling.entity.CarEntity;
import com.cabify.pooling.entity.GroupOfPeopleEntity;
import com.cabify.pooling.exception.GroupAlreadyExistsException;
import com.cabify.pooling.repository.CarsRepository;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DataMongoTest
@RunWith(SpringRunner.class)
public class CarPoolingServiceTest {

	@Autowired
	private CarsRepository carsRepository;
	@Autowired
	private ReactiveMongoTemplate mongoTemplate;

	private CarPoolingService carPoolingService;

	@Before
	public void before() {
		carPoolingService = new CarPoolingService(carsRepository, mongoTemplate);
	}

	@Test
	public void GivenCarsWithAvailableSeats_WhenJourney_ThenCarAsigned() throws GroupAlreadyExistsException {
		CarDTO expectedCar = new CarDTO(1, 3);
		carPoolingService.createCars(Arrays.asList(expectedCar)).blockLast();

		GroupOfPeopleDTO requestedGroup = new GroupOfPeopleDTO(1, 2);
		Mono<CarEntity> result = carPoolingService.journey(requestedGroup);

		GroupOfPeopleEntity expectedGroup = new GroupOfPeopleEntity(requestedGroup.getId(), requestedGroup.getPeople());
		StepVerifier.create(result).expectNextMatches(assignedCar -> expectedCar.getId() == assignedCar.getId()
				&& expectedCar.getSeats() - requestedGroup.getPeople() == assignedCar.getSeatsAvailable()
				&& assignedCar.getGroups().size() == 1
				&& assignedCar.getGroups().contains(expectedGroup)).verifyComplete();
	}

	@Test
	public void GivenCarsWithoutEnoughAvailableSeats_WhenJourney_ThenCarUnasigned() throws GroupAlreadyExistsException {
		carPoolingService.createCars(Arrays.asList(new CarDTO(1, 3)))
			.then(carPoolingService.journey(new GroupOfPeopleDTO(1, 2))).block();

		GroupOfPeopleDTO requestedGroup = new GroupOfPeopleDTO(2, 2);
		Mono<CarEntity> result = carPoolingService.journey(requestedGroup);

		StepVerifier.create(result).verifyComplete();
	}

}
