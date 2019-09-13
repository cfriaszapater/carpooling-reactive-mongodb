package com.cabify.pooling.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.cabify.pooling.entity.CarEntity;

public interface CarsRepository extends ReactiveMongoRepository<CarEntity, Integer>, CustomizedCarsRepository {
}
