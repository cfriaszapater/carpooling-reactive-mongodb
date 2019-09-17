package com.cabify.pooling.repository;

import com.cabify.pooling.entity.GroupOfPeopleEntity;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface GroupsRepository extends ReactiveMongoRepository<GroupOfPeopleEntity, Integer> {
}
