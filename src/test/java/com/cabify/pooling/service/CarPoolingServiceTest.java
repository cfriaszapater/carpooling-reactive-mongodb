package com.cabify.pooling.service;

import com.cabify.pooling.dto.CarDTO;
import com.cabify.pooling.dto.GroupOfPeopleDTO;
import com.cabify.pooling.entity.CarEntity;
import com.cabify.pooling.entity.GroupOfPeopleEntity;
import com.cabify.pooling.repository.CarsRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.test.context.junit4.SpringRunner;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

@DataMongoTest
@RunWith(SpringRunner.class)
@Slf4j
public class CarPoolingServiceTest {

	@Autowired
	private CarsRepository carsRepository;

	private CarPoolingService carPoolingService;

	@Before
	public void before() {
		Hooks.onOperatorDebug();
		carsRepository.initWith(Flux.empty()).blockLast();
		carPoolingService = new CarPoolingService(carsRepository);
	}

	@Test
	public void GivenCarWithAvailableSeats_WhenJourney_ThenCarAssigned() {
		CarDTO expectedCar = new CarDTO(1, 3);
		carPoolingService.createCars(Collections.singletonList(expectedCar)).blockLast();

		GroupOfPeopleDTO requestedGroup = new GroupOfPeopleDTO(1, 2);
		Mono<CarEntity> result = carPoolingService.journey(requestedGroup);

		GroupOfPeopleEntity expectedGroup = GroupOfPeopleEntity.builder().id(requestedGroup.getId()).build();
		StepVerifier.create(result).expectNextMatches(
				assignedCar -> expectedCar.getId() == assignedCar.getId() &&
						expectedCar.getSeats() - requestedGroup.getPeople() == assignedCar.getSeatsAvailable() &&
						assignedCar.getGroups().size() == 1 &&
						assignedCar.getGroups().contains(expectedGroup)).verifyComplete();
	}

	@Test
	public void GivenCarsWithAvailableSeats_WhenJourney_ThenCarAssignedWithLeastNeededAvailableSeats() {
		CarDTO expectedCar = new CarDTO(3, 3);
		carPoolingService.createCars(Arrays.asList(new CarDTO(1, 1), new CarDTO(2, 6), expectedCar)).blockLast();

		GroupOfPeopleDTO requestedGroup = new GroupOfPeopleDTO(1, 2);
		Mono<CarEntity> result = carPoolingService.journey(requestedGroup);

		GroupOfPeopleEntity expectedGroup = GroupOfPeopleEntity.builder().id(requestedGroup.getId()).build();
		StepVerifier.create(result).expectNextMatches(assignedCar -> expectedCar.getId() == assignedCar.getId()
				&& expectedCar.getSeats() - requestedGroup.getPeople() == assignedCar.getSeatsAvailable()
				&& assignedCar.getGroups().size() == 1
				&& assignedCar.getGroups().contains(expectedGroup)).verifyComplete();
	}

	@Test
	public void GivenCarsWithoutEnoughAvailableSeats_WhenJourney_ThenCarUnassigned_AndWaiting() {
		carPoolingService.createCars(Collections.singletonList(new CarDTO(1, 3)))
				.then(carPoolingService.journey(new GroupOfPeopleDTO(1, 2))).block();

		GroupOfPeopleDTO requestedGroup = new GroupOfPeopleDTO(2, 2);
		Mono<CarEntity> result = carPoolingService.journey(requestedGroup);

		StepVerifier.create(result).expectNextMatches(car -> car.getId().equals(CarsRepository.WAITING_GROUPS)).verifyComplete();
		StepVerifier.create(carPoolingService.waitingGroups()).expectNextMatches(g -> g.getId().equals(requestedGroup.getId())).verifyComplete();
	}

	@Test
	public void GivenCarAssigned_WhenDropoff_ThenSeatsFreed() {
		CarDTO expectedCar = new CarDTO(1, 3);
		GroupOfPeopleDTO requestedGroup = new GroupOfPeopleDTO(1, 2);
		Mono<CarEntity> given = carPoolingService.createCars(Collections.singletonList(expectedCar))
				.then(carPoolingService.journey(requestedGroup));

		Mono<CarEntity> result = given.then(carPoolingService.dropoff(requestedGroup.getId()));

		StepVerifier.create(result).expectNextMatches(droppedCar -> expectedCar.getSeats() == droppedCar.getSeatsAvailable())
				.verifyComplete();
	}

	@Test
	public void GivenGroupAssigned_WhenLocate_ThenGroupFound() {
		CarDTO expectedCar = new CarDTO(1, 3);
		GroupOfPeopleDTO requestedGroup = new GroupOfPeopleDTO(1, 2);
		Mono<CarEntity> given = carPoolingService.createCars(Collections.singletonList(expectedCar))
				.then(carPoolingService.journey(requestedGroup));

		Mono<GroupOfPeopleEntity> result = given.then(carPoolingService.locateGroup(requestedGroup.getId()));

		GroupOfPeopleEntity expectedGroup = GroupOfPeopleEntity.builder().id(requestedGroup.getId()).build();
		StepVerifier.create(result).expectNext(expectedGroup).verifyComplete();
	}

	@Test
	public void GivenGroupUnassigned_WhenLocate_ThenGroupNotFound() {
		int requestedGroupId = randomId();
		Mono<CarEntity> given = carPoolingService.createCars(Collections.singletonList(new CarDTO(1, 3)))
				.then(carPoolingService.journey(new GroupOfPeopleDTO(requestedGroupId, 5)));

		Mono<GroupOfPeopleEntity> result = given.then(carPoolingService.locateGroup(requestedGroupId));

		StepVerifier.create(result).verifyComplete();
	}

	@Test
	public void GivenGroupAssigned_AndDroppedoff_WhenLocate_ThenGroupNotFound() {
		CarDTO expectedCar = new CarDTO(1, 3);
		GroupOfPeopleDTO requestedGroup = new GroupOfPeopleDTO(1, 2);
		Mono<CarEntity> given = carPoolingService.createCars(Collections.singletonList(expectedCar))
				.then(carPoolingService.journey(requestedGroup))
				.then(carPoolingService.dropoff(requestedGroup.getId()));

		Mono<GroupOfPeopleEntity> result = given.then(carPoolingService.locateGroup(requestedGroup.getId()));

		StepVerifier.create(result).verifyComplete();
	}

	@Test
	public void GivenGroupNotAssigned_WhenDropoff_ThenRemovedFromWaitingGroups() {
		GroupOfPeopleDTO group = new GroupOfPeopleDTO(2, 6);
		Mono<CarEntity> given = carPoolingService.createCars(Collections.singletonList(new CarDTO(1, 3)))
				.then(carPoolingService.journey(group));

		Mono<CarEntity> result = given.then(carPoolingService.dropoff(group.getId()));

		StepVerifier.create(result).expectNextMatches(car -> car.getId().equals(CarsRepository.WAITING_GROUPS)).verifyComplete();
	}

	@Test
	public void GivenCarsAndJourneys_WhenPutCars_ThenNewCars_AndNoJourneys() {
		int givenGroupId = 42;
		Mono<CarEntity> givenGroup = carPoolingService.createCars(Arrays.asList(new CarDTO(1, 3), new CarDTO(2, 5)))
				.then(carPoolingService.journey(new GroupOfPeopleDTO(givenGroupId, 2)));

		int expectedCarId = 13;
		Flux<CarEntity> result = givenGroup
				.thenMany(carPoolingService.createCars(Arrays.asList(new CarDTO(expectedCarId, 4), new CarDTO(14, 5), new CarDTO(15, 6))));

		result.blockLast();
		StepVerifier.create(carPoolingService.locateGroup(givenGroupId)).verifyComplete();
		StepVerifier.create(carPoolingService.journey(new GroupOfPeopleDTO(7, 3))).expectNextMatches(car -> car.getId() == expectedCarId).verifyComplete();
	}

	@Test
	public void GivenGroupWaiting_WhenOtherGroupDropoff_AndEnoughAvailableSeats_ThenReassigned() {
		CarDTO expectedCar = new CarDTO(randomId(), 6);
		int assignedGroupId = 1;
		int unassignedGroupId = 2;
		carPoolingService.createCars(Arrays.asList(new CarDTO(randomId(), 4), expectedCar))
				.then(carPoolingService.journey(new GroupOfPeopleDTO(assignedGroupId, 5)))
				.then(carPoolingService.journey(new GroupOfPeopleDTO(unassignedGroupId, 6)))
				.block();
		log.info("given waitingGroups: {}", carPoolingService.waitingGroups().collectList().block());
		log.info("given cars: {}", carPoolingService.cars().collectList().block());

		carPoolingService.dropoff(assignedGroupId).subscribe();

		await().atMost(1, SECONDS).until(() -> groupReassigned(unassignedGroupId));
		Mono<CarEntity> finallyAssignedCar = carPoolingService.locateCarOfGroup(unassignedGroupId);
		StepVerifier.create(finallyAssignedCar).expectNextMatches(car -> expectedCar.getId() == car.getId()).verifyComplete();
		StepVerifier.create(carPoolingService.waitingGroups()).expectComplete();
		logCarsAndWaitingGroups();
	}

	@Test
	public void GivenGroupsWaiting_WhenOtherGroupDropoff_AndEnoughAvailableSeats_ThenReassignedFIFO() {
		log.info("BEGIN GivenGroupsWaiting_WhenOtherGroupDropoff_AndEnoughAvailableSeats_ThenReassignedFIFO");
		CarDTO expectedCar = new CarDTO(randomId(), 6);
		int assignedGroupId = 1;
		int unassignedGroupId1 = 2;
		int unassignedGroupId2 = 3;
		int unassignedGroupId3 = 4;
		int unassignedGroupId4 = 5;
		carPoolingService.createCars(Arrays.asList(new CarDTO(randomId(), 1), expectedCar))
				.then(carPoolingService.journey(new GroupOfPeopleDTO(assignedGroupId, 6)))
				.then(carPoolingService.journey(new GroupOfPeopleDTO(unassignedGroupId1, 2)))
				.then(carPoolingService.journey(new GroupOfPeopleDTO(unassignedGroupId2, 2)))
				.then(carPoolingService.journey(new GroupOfPeopleDTO(unassignedGroupId3, 2)))
				.then(carPoolingService.journey(new GroupOfPeopleDTO(unassignedGroupId4, 2)))
				.block();
		log.info("given waitingGroups: {}", carPoolingService.waitingGroups().collectList().block());
		log.info("given cars: {}", carPoolingService.cars().collectList().block());

		carPoolingService.dropoff(assignedGroupId).subscribe();

		await().atMost(1, SECONDS).until(() -> {
			logCarsAndWaitingGroups();
			return groupReassigned(unassignedGroupId1) && groupReassigned(unassignedGroupId2) && groupReassigned(unassignedGroupId3);
		});
		logCarsAndWaitingGroups();
		StepVerifier.create(carPoolingService.locateCarOfGroup(unassignedGroupId1)).expectNextMatches(car -> expectedCar.getId() == car.getId()).verifyComplete();
		StepVerifier.create(carPoolingService.locateCarOfGroup(unassignedGroupId2)).expectNextMatches(car -> expectedCar.getId() == car.getId()).verifyComplete();
		StepVerifier.create(carPoolingService.locateCarOfGroup(unassignedGroupId3)).expectNextMatches(car -> expectedCar.getId() == car.getId()).verifyComplete();
		// FIFO - The last one to request journey is the one that is not assigned
		StepVerifier.create(carPoolingService.locateCarOfGroup(unassignedGroupId4)).verifyComplete();
		StepVerifier.create(carPoolingService.waitingGroups()).expectNextMatches(g -> g.getId().equals(unassignedGroupId4)).verifyComplete();

		log.info("END GivenGroupsWaiting_WhenOtherGroupDropoff_AndEnoughAvailableSeats_ThenReassignedFIFO");
	}

	private boolean groupReassigned(int unassignedGroupId) {
		return carPoolingService.locateCarOfGroup(unassignedGroupId).block() != null;
	}

	private int randomId() {
		return ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
	}

	@Test
	public void GivenGroupWaiting_WhenDropoff_ThenRemovedFromWaitingGroups() {
		GroupOfPeopleDTO group = new GroupOfPeopleDTO(1, 2);
		carPoolingService.journey(group).block();
		log.info("given waitingGroups: {}", carPoolingService.waitingGroups().collectList().block());
		log.info("given cars: {}", carPoolingService.cars().collectList().block());

		Mono<CarEntity> result = carPoolingService.dropoff(group.getId());

		StepVerifier.create(result).expectNextMatches(car -> car.getId().equals(CarsRepository.WAITING_GROUPS)).verifyComplete();
		logCarsAndWaitingGroups();
		StepVerifier.create(carPoolingService.waitingGroups()).verifyComplete();
	}

	private void logCarsAndWaitingGroups() {
		log.info("then waitingGroups: {}", carPoolingService.waitingGroups().collectList().block());
		log.info("then cars: {}", carPoolingService.cars().collectList().block());
	}

}
