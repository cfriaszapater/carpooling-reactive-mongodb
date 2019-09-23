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
				.switchIfEmpty(carsRepository.putInWaitingQueue(group).then(Mono.empty()));
	}

	/**
	 * @return car if dropped off (group was assigned to car), empty otherwise.
	 */
	public Mono<CarEntity> dropoff(Integer groupId) {
		Mono<CarEntity> droppedOff = carsRepository.removeGroupFromCarAndFreeSeats(groupId);

		// Fire asynchronous reassign (to start after droppedOff stream is emitted)
		return droppedOff.doOnSuccess(car -> reAssignWaitingGroups());
	}

	private void reAssignWaitingGroups() {
		// TODO For concurrency: a) mark reassigningSince + select where reassigningSince = null + async crashRecovery sets reassigningSince = null, or b) findAndRemove + transaction

		// Thread-safety: sets reassigningSince + select where reassigningSince = null.
		// XXX Additionally, in case process crashed while reassigning, async crashRecoverer would set reassigningSince = null to those that have been reassigningSince more than a max timeout

		Flux<GroupOfPeopleEntity> reassigningGroups = carsRepository.findWaitingAndSetReassigning()
				.log("reassigningGroups");
		Flux<GroupOfPeopleEntity> reassignedGroups = reassigningGroups
				// concatMap to assign in order
				.concatMap(group ->
						carsRepository
								.assignToCarWithAvailableSeats(group)
								.flatMap(car -> Mono.just(group))
				)
				.log("reassignedGroups");
		reassignedGroups
				.flatMap(carsRepository::findWaitingReassigningByIdAndDelete)
				.log("waitingGroupsAfterDelete")
				.then(carsRepository.findReassigningAndUnset())
				.subscribe(g -> {
				}, err -> log.error(err.getMessage(), err));
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

	public Mono<CarEntity> removeWaitingGroup(Integer id) {
		return carsRepository.deleteWaitingById(id);
	}

	Flux<GroupOfPeopleEntity> waitingGroups() {
		return carsRepository.findAllGroupsWaiting();
	}

	Flux<CarEntity> cars() {
		return carsRepository.findAllNotWaiting();
	}
}
