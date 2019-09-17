package com.cabify.pooling.repository;

import com.cabify.pooling.entity.CarEntity;
import com.cabify.pooling.entity.GroupOfPeopleEntity;
import reactor.core.publisher.Mono;

public interface CustomizedCarsRepository {
	Mono<CarEntity> assignToCarWithAvailableSeats(GroupOfPeopleEntity group);

	Mono<CarEntity> removeGroupFromCarAndFreeSeats(Integer groupId);

	Mono<GroupOfPeopleEntity> locateGroupById(Integer groupId);

	Mono<CarEntity> locateCarOfGroup(Integer groupId);
}
