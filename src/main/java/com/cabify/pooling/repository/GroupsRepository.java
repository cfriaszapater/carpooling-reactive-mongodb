package com.cabify.pooling.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.cabify.pooling.entity.GroupOfPeopleEntity;

public interface GroupsRepository extends ReactiveMongoRepository<GroupOfPeopleEntity, Integer> {
}
