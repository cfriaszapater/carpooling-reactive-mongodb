package com.cabify.pooling.service;

import java.util.List;

import javax.validation.Valid;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.cabify.pooling.dto.CarDTO;
import com.cabify.pooling.dto.GroupOfPeopleDTO;
import com.cabify.pooling.entity.CarEntity;
import com.cabify.pooling.entity.GroupOfPeopleEntity;
import com.cabify.pooling.repository.CarsRepository;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class CarPoolingService {

	private final CarsRepository carsRepository;

	private final ReactiveMongoTemplate mongoTemplate;

	public Flux<CarEntity> createCars(@Valid List<CarDTO> carDtos) {
		// Clear all info and store cars
		Flux<CarEntity> carEntities = Flux
				.fromStream(carDtos.stream().map(requestedCar -> new CarEntity(requestedCar.getId(), requestedCar.getSeats(), null)));
		return carsRepository.deleteAll().thenMany(carsRepository.saveAll(carEntities));
	}

	public Mono<CarEntity> journey(@Valid GroupOfPeopleDTO groupDto) {
		return assignToCarWithAvailableSeats(new GroupOfPeopleEntity(groupDto.getId(), groupDto.getPeople()));
	}

	private Mono<CarEntity> assignToCarWithAvailableSeats(GroupOfPeopleEntity group) {
		Query query = Query.query(Criteria.where("id").is(1).and("seatsAvailable").gte(group.getPeople()));
		Update update = new Update()
				.inc("seatsAvailable", -group.getPeople())
				.addToSet("groups").value(group);
		return mongoTemplate.findAndModify(query, update, new FindAndModifyOptions().returnNew(true), CarEntity.class);
	}

}
