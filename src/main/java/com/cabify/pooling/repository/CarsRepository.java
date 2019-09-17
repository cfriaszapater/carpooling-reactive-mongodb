package com.cabify.pooling.repository;

import com.cabify.pooling.entity.CarEntity;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface CarsRepository extends ReactiveMongoRepository<CarEntity, Integer>, CustomizedCarsRepository {
}
