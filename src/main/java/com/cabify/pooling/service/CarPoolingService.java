package com.cabify.pooling.service;

import java.util.List;

import javax.validation.Valid;

import org.springframework.stereotype.Service;

import com.cabify.pooling.dto.CarDTO;
import com.cabify.pooling.dto.GroupOfPeopleDTO;
import com.cabify.pooling.entity.CarEntity;
import com.cabify.pooling.entity.GroupOfPeopleEntity;
import com.cabify.pooling.repository.CarsRepository;
import com.cabify.pooling.repository.GroupsRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class CarPoolingService {

	private final CarsRepository carsRepository;
	private final GroupsRepository waitingGroupsRepository;

	public Flux<CarEntity> createCars(@Valid List<CarDTO> carDtos) {
		// Clear all info and store cars
		Flux<CarEntity> carEntities = Flux
				.fromStream(carDtos.stream().map(requestedCar -> new CarEntity(requestedCar.getId(), requestedCar.getSeats(), null)));
		return carsRepository.deleteAll().thenMany(carsRepository.saveAll(carEntities));
	}

	public Mono<CarEntity> journey(@Valid GroupOfPeopleDTO groupDto) {
		GroupOfPeopleEntity group = new GroupOfPeopleEntity(groupDto.getId(), groupDto.getPeople());
		return carsRepository.assignToCarWithAvailableSeats(group)
				.switchIfEmpty(waitingGroupsRepository.save(group).then(Mono.empty()));
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
		// TODO Auto-generated method stub
		
	}

	/**
	 * Locate group in cars, return group.
	 * @return group if assigned, or empty if group is not assigned to any car.
	 */
	public Mono<GroupOfPeopleEntity> locateGroup(Integer groupId) {
		return carsRepository.locateGroupById(groupId);
	}

	/**
	 * Locate group in cars, return car.
	 * @return car if assigned, or empty if group is not assigned to any car.
	 */
	public Mono<CarEntity> locateCarOfGroup(int groupId) {
		return carsRepository.locateCarOfGroup(groupId);
	}

	public Mono<GroupOfPeopleEntity> findWaitingGroup(Integer id) {
		return waitingGroupsRepository.findById(id);
	}

	public Mono<Void> removeWaitingGroup(Integer id) {
		log.info("removeWaitingGroup {}", id);
		return waitingGroupsRepository.deleteById(id);
	}
}
