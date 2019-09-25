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
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@DataMongoTest
@RunWith(SpringRunner.class)
@Slf4j
public class CarPoolingServiceConcurrentTest {

  private static final int CONCURRENT_REQUESTS = 20;

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

  @SuppressWarnings("SameReturnValue")
  private void thenAssignedGroups(int expectedGroupsAssigned) {
    await().atMost(1, SECONDS).ignoreExceptions().until(() -> {
      logCarsAndWaitingGroups();

      StepVerifier.create(
        carPoolingService.cars()
          .map(car -> car.getGroups().size())
          .reduce(Integer::sum)
      ).expectNext(expectedGroupsAssigned).verifyComplete();
      return true;
    });
  }

  private void logCarsAndWaitingGroups() {
    List<GroupOfPeopleEntity> waitingGroups = carPoolingService.waitingGroups().collectList().block();
    log.info("waitingGroups ({}): {}", waitingGroups != null ? waitingGroups.size() : 0, waitingGroups);
    List<CarEntity> cars = carPoolingService.cars().collectList().block();
    assert cars != null;
    Optional<Integer> assignedGroups = cars.stream().map(car -> car.getGroups().size()).reduce(Integer::sum);
    log.info("cars ({} with {} assigned groups): {}", cars.size(), assignedGroups.orElse(0), cars);
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

  @Test
  public void GivenCarsAssigned_WhenConcurrentPostDropoff_ThenCarsUnassigned() throws InterruptedException {
    final int numberOfIterations = 50;
    for (int i = 0; i < numberOfIterations; i++) {
      log.trace("iteration {} starts...", i);
      List<CarDTO> cars = new ArrayList<>();
      for (int j = 0; j < CONCURRENT_REQUESTS; j++) {
        cars.add(new CarDTO(j, 4));

      }
      carPoolingService.createCars(cars).blockLast();
      List<GroupOfPeopleEntity> createdGroups = concurrentPostJourneys();
      logCarsAndWaitingGroups();

      List<CarEntity> droppedCars = concurrentPostDropoff(createdGroups);

      thenAssignedGroups(0);
      assertEquals(CONCURRENT_REQUESTS, droppedCars.size());
      log.trace("...iteration {} ends", i);
    }
  }

  private List<CarEntity> concurrentPostDropoff(List<GroupOfPeopleEntity> groups) throws InterruptedException {
    CountDownLatch startGate = new CountDownLatch(1);
    CountDownLatch finishLine = new CountDownLatch(groups.size());

    List<CarEntity> droppedCars = Collections.synchronizedList(new ArrayList<>());
    for (GroupOfPeopleEntity group : groups) {
      final int groupId = group.getId();
      Thread thread = new Thread(() -> {
        try {
          log.trace("{} awaiting at start gate...", groupId);
          startGate.await();

          CarEntity dropped = carPoolingService.dropoff(groupId).block();
          if (dropped != null && !CarsRepository.WAITING_GROUPS.equals(dropped.getId())) {
            droppedCars.add(dropped);
          }

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
    return droppedCars;
  }

  @Test
  public void GivenCarsAssigned_AndWaitingGroups_WhenConcurrentPostDropoff_ThenWaitingCarsAssignedAsynchronously() throws InterruptedException {
    final int numberOfIterations = 50;
    for (int i = 0; i < numberOfIterations; i++) {
      log.info("iteration {} starts...", i);
      List<CarDTO> cars = new ArrayList<>();
      for (int j = 0; j < CONCURRENT_REQUESTS; j++) {
        cars.add(new CarDTO(j, 4));

      }
      carPoolingService.createCars(cars).blockLast();
      List<GroupOfPeopleEntity> createdGroups = concurrentPostJourneys(CONCURRENT_REQUESTS * 2);
      log.info("createdGroups ({}) = {}", createdGroups.size(), createdGroups);
      List<GroupOfPeopleEntity> assignedGroups = carPoolingService.cars()
        .flatMap(car -> Flux.fromIterable(car.getGroups())).collectList().block();
      log.info("assignedGroups ({}) = {}", assignedGroups != null ? assignedGroups.size() : 0, assignedGroups);
      logCarsAndWaitingGroups();

      assert assignedGroups != null;
      List<CarEntity> droppedCars = concurrentPostDropoff(assignedGroups);
      log.info("droppedCars ({}) = {}", droppedCars.size(), droppedCars);

      assertEquals(CONCURRENT_REQUESTS, droppedCars.size());
      thenAssignedGroups(CONCURRENT_REQUESTS);
      log.info("...iteration {} ends", i);
    }
  }

  private List<GroupOfPeopleEntity> concurrentPostJourneys(int concurrentRequests) throws InterruptedException {
    CountDownLatch startGate = new CountDownLatch(1);
    CountDownLatch finishLine = new CountDownLatch(concurrentRequests);

    List<GroupOfPeopleEntity> groups = Collections.synchronizedList(new ArrayList<>());
    for (int i = 0; i < concurrentRequests; i++) {
      final int groupId = i;
      Thread thread = new Thread(() -> {
        try {
          log.trace("{} awaiting at start gate...", groupId);
          startGate.await();

          GroupOfPeopleDTO group = new GroupOfPeopleDTO(groupId, 4);
          carPoolingService.journey(group).block();
          groups.add(GroupOfPeopleEntity.builder().id(groupId).people(group.getPeople()).build());

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

    return groups;

  }

  private List<GroupOfPeopleEntity> concurrentPostJourneys() throws InterruptedException {
    return concurrentPostJourneys(CONCURRENT_REQUESTS);
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
            .then(carPoolingService.dropoff(groupId))
            .block();

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
      logCarsAndWaitingGroups();
      fail("some concurrent request failed");
    }
  }
}
