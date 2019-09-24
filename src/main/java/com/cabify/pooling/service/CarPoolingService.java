package com.cabify.pooling.service;

import com.cabify.pooling.dto.CarDTO;
import com.cabify.pooling.dto.GroupOfPeopleDTO;
import com.cabify.pooling.entity.CarEntity;
import com.cabify.pooling.entity.GroupOfPeopleEntity;
import com.cabify.pooling.repository.CarsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.validation.Valid;
import java.time.Duration;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CarPoolingService {

	private final CarsRepository carsRepository;

	public Flux<CarEntity> createCars(@Valid List<CarDTO> carDtos) {
		// Clear all info and store cars
		Flux<CarEntity> carEntities = Flux
				.fromStream(carDtos.stream().map(requestedCar -> CarEntity.builder().id(requestedCar.getId()).seatsAvailable(requestedCar.getSeats()).build()));
		return carsRepository.initWith(carEntities);
	}

	public Mono<CarEntity> journey(@Valid GroupOfPeopleDTO groupDto) {
		GroupOfPeopleEntity group = new GroupOfPeopleEntity(groupDto.getId(), groupDto.getPeople(), new Date());
		return carsRepository.assignToCarWithAvailableSeats(group)
				.log("assignToCarWithAvailableSeats of group" + group.getId())
				.switchIfEmpty(carsRepository.putInWaitingQueue(group)
						.log("putInWaitingQueue of group" + group.getId())
				)
				;
	}

	/**
	 * @return car if dropped off (group was assigned to car), empty otherwise.
	 */
	public Mono<CarEntity> dropoff(Integer groupId) {
		Mono<CarEntity> droppedOff = carsRepository.dropoff(groupId)
				.log("after_dropoff of group" + groupId)
				.next();

		// Fire asynchronous reassign (to start after droppedOff stream is emitted)
		return droppedOff.doOnSuccess(car -> reAssignWaitingGroups());
	}

	private void reAssignWaitingGroups() {
		// Thread-safety: see carsRepository.reassign()

		carsRepository.findAllGroupsWaiting()
				// concatMap to preserve order
				.concatMap(carsRepository::reassign)
				.log("after_reassign")
				.retryBackoff(3, Duration.ofMillis(100))
				.subscribe(g -> log.info("reassigned group {}", g), err -> log.error(err.getMessage(), err));
	}

	/**
	 * Locate group in cars, return group.
	 *
	 * @return group if assigned, or empty if group is not assigned to any car.
	 */
	Mono<GroupOfPeopleEntity> locateGroup(Integer groupId) {
		return carsRepository.locateGroupById(groupId);
	}

	/**
	 * Locate group in cars, return car.
	 *
	 * @return car if assigned, or empty if group is not assigned to any car.
	 */
	public Mono<CarEntity> locateCarOfGroup(int groupId) {
		return carsRepository.locateCarOfGroup(groupId);
	}

	public Mono<GroupOfPeopleEntity> findWaitingGroup(Integer id) {
		return carsRepository.findWaitingById(id);
	}

	Flux<GroupOfPeopleEntity> waitingGroups() {
		return carsRepository.findAllGroupsWaiting();
	}

	Flux<CarEntity> cars() {
		return carsRepository.findAllNotWaiting();
	}
}
