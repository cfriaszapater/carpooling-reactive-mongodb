package com.cabify.pooling.service;

import com.cabify.pooling.dto.CarDTO;
import com.cabify.pooling.dto.GroupOfPeopleDTO;
import com.cabify.pooling.repository.CarsRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.test.context.junit4.SpringRunner;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.fail;

@DataMongoTest
@RunWith(SpringRunner.class)
@Slf4j
public class CarPoolingServiceConcurrentTest {

	@Autowired
	private CarsRepository carsRepository;

	private CarPoolingService carPoolingService;

	@Before
	public void before() {
		carsRepository.deleteAll().block();
		carPoolingService = new CarPoolingService(carsRepository);
	}

	@Test
	public void GivenCarWith4SeatsAvailable_WhenConcurrentPostJourneysOf4_ThenCarAssignedToOnlyOne() throws InterruptedException {
		final int numberOfIterations = 10;
		for (int i = 0; i < numberOfIterations; i++) {
			log.info("iteration {} starts...", i);
			carPoolingService.createCars(Collections.singletonList(new CarDTO(1, 4))).blockLast();

			concurrentPostJourneys();

			thenAssignedGroups(1);
			log.info("...iteration {} ends", i);
		}
	}

	private void thenAssignedGroups(int expectedGroupsAssigned) {
		await().atMost(1, SECONDS).ignoreExceptions().until(() -> {
			StepVerifier.create(
					carPoolingService.cars()
							.map(car -> car.getGroups().size())
							.reduce(Integer::sum)
			).expectNext(expectedGroupsAssigned).verifyComplete();
			return true;
		});
	}

	@Test
	public void GivenCarWith4SeatsAvailable_WhenConcurrentPostJourneysOf4_AndDropoff_ThenCarUnassigned() throws InterruptedException {
		final int numberOfIterations = 10;
		for (int i = 0; i < numberOfIterations; i++) {
			log.info("iteration {} starts...", i);
			carPoolingService.createCars(Collections.singletonList(new CarDTO(1, 4))).blockLast();

			concurrentPostJourneysAndDropoff();

			thenAssignedGroups(0);
			log.info("...iteration {} ends", i);
		}
	}

//	@Test
//	public void GivenCarsAssigned_WhenConcurrentPostDropoff_ThenCarsUnassigned() throws InterruptedException {
//		final int numberOfIterations = 100;
//		final int numberOfConcurrentRequests = 10;
//		for (int i = 0; i < numberOfIterations; i++) {
//			log.trace("iteration {} starts...", i);
//			List<CarDTO> cars = new ArrayList<>();
//			for (int j = 0; j < numberOfConcurrentRequests; j++) {
//				cars.add(new CarDTO(j, 4));
//
//			}
//			carPoolingService.createCars(cars);
//
//			List<GroupOfPeopleEntity> createdGroups = concurrentPostJourneys(numberOfConcurrentRequests);
//
//			List<Optional<CarEntity>> droppedCars = concurrentPostDropoff(createdGroups);
//
//			thenDroppedCars(droppedCars, 10);
//			thenCarAssignedToGroups(carPoolingService.groups(), 0);
//			log.trace("...iteration {} ends", i);
//		}
//	}
//
//	@Test
//	public void GivenCarsAssigned_AndWaitingGroups_WhenConcurrentPostDropoff_ThenWaitingCarsAssignedSynchronously() throws InterruptedException {
//		final int numberOfIterations = 100;
//		final int numberOfConcurrentRequests = 10;
//		for (int i = 0; i < numberOfIterations; i++) {
//			log.trace("iteration {} starts...", i);
//			List<CarDTO> cars = new ArrayList<>();
//			for (int j = 0; j < numberOfConcurrentRequests; j++) {
//				cars.add(new CarDTO(j, 4));
//
//			}
//			carPoolingService.createCars(cars);
//
//			List<GroupOfPeopleEntity> createdGroups = concurrentPostJourneys(numberOfConcurrentRequests * 2);
//
//			List<GroupOfPeopleEntity> assignedGroups = createdGroups.stream().filter(group -> group.getCar() != null).collect(Collectors.toList());
//			List<Optional<CarEntity>> droppedCars = concurrentPostDropoff(assignedGroups);
//
//			thenDroppedCars(droppedCars, 10);
//			thenCarAssignedToGroups(carPoolingService.groups(), numberOfConcurrentRequests);
//			log.trace("...iteration {} ends", i);
//		}
//	}

	private void concurrentPostJourneys() throws InterruptedException {
		CountDownLatch startGate = new CountDownLatch(1);
		CountDownLatch finishLine = new CountDownLatch(20);

		for (int i = 0; i < 20; i++) {
			final int groupId = i;
			Thread thread = new Thread(() -> {
				try {
					log.trace("{} awaiting at start gate...", groupId);
					startGate.await();

					carPoolingService.journey(new GroupOfPeopleDTO(groupId, 4)).block();

					finishLine.countDown();
					log.trace("...{} crossed finish line", groupId);
				} catch (InterruptedException e) {
					log.warn(e.getMessage(), e);
				}
			});
			thread.start();
		}

		startGate.countDown();
		boolean allThreadsReachedFinishLine = finishLine.await(5, TimeUnit.SECONDS);
		if (!allThreadsReachedFinishLine) {
			fail("some concurrent request failed");
		}
	}

	private void concurrentPostJourneysAndDropoff() throws InterruptedException {
		CountDownLatch startGate = new CountDownLatch(1);
		CountDownLatch finishLine = new CountDownLatch(10);

		for (int i = 0; i < 10; i++) {
			final int groupId = i;
			Thread thread = new Thread(() -> {
				try {
					log.trace("{} awaiting at start gate...", groupId);
					startGate.await();

					carPoolingService.journey(new GroupOfPeopleDTO(groupId, 4))
							.then(carPoolingService.dropoff(groupId)).block();

					finishLine.countDown();
					log.trace("...{} crossed finish line", groupId);
				} catch (InterruptedException e) {
					log.warn(e.getMessage(), e);
				}
			});
			thread.start();
		}

		startGate.countDown();
		boolean allThreadsReachedFinishLine = finishLine.await(5, TimeUnit.SECONDS);
		if (!allThreadsReachedFinishLine) {
			fail("some concurrent request failed");
		}
	}

//	private List<Optional<CarEntity>> concurrentPostDropoff(List<GroupOfPeopleEntity> groups) throws InterruptedException {
//		CountDownLatch startGate = new CountDownLatch(1);
//		CountDownLatch finishLine = new CountDownLatch(groups.size());
//
//		List<Optional<CarEntity>> droppedCars = Collections.synchronizedList(new ArrayList<>());
//		for (GroupOfPeopleEntity group : groups) {
//			final int groupId = group.getId();
//			Thread thread = new Thread() {
//				public void run() {
//					try {
//						log.trace("{} awaiting at start gate...", groupId);
//						startGate.await();
//
//						droppedCars.add(carPoolingService.dropoff(groupId));
//
//						finishLine.countDown();
//						log.trace("...{} crossed finish line", groupId);
//					} catch (InterruptedException e) {
//						log.warn(e.getMessage(), e);
//					}
//				}
//			};
//			thread.start();
//		}
//
//		startGate.countDown();
//		boolean allThreadsReachedFinishLine = finishLine.await(5, TimeUnit.SECONDS);
//		if (!allThreadsReachedFinishLine) {
//			fail("some concurrent request failed");
//		}
//		return droppedCars;
//	}

}
