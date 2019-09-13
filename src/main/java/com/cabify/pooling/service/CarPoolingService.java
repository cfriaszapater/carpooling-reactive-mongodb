package com.cabify.pooling.service;

import java.util.List;

import javax.validation.Valid;

import org.springframework.stereotype.Service;

import com.cabify.pooling.dto.CarDTO;
import com.cabify.pooling.dto.GroupOfPeopleDTO;
import com.cabify.pooling.entity.CarEntity;
import com.cabify.pooling.repository.CarsRepository;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class CarPoolingService {

	private final CarsRepository carsRepository;

	public Flux<CarEntity> createCars(@Valid List<CarDTO> carDtos) {
		// Clear all info and store cars
		Flux<CarEntity> carEntities = Flux
				.fromStream(carDtos.stream().map(requestedCar -> new CarEntity(requestedCar.getId(), requestedCar.getSeats())));
		return carsRepository.deleteAll().thenMany(carsRepository.saveAll(carEntities));
	}

	public void journey(@Valid GroupOfPeopleDTO group) {
		// TODO Auto-generated method stub
		
	}

}
