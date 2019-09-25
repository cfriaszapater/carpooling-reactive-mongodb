package com.cabify.pooling.controller;

import com.cabify.pooling.dto.CarDTO;
import com.cabify.pooling.dto.GroupOfPeopleDTO;
import com.cabify.pooling.dto.GroupOfPeopleForm;
import com.cabify.pooling.entity.CarEntity;
import com.cabify.pooling.service.CarPoolingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.validation.Valid;
import java.util.List;

import static com.cabify.pooling.repository.CarsRepository.WAITING_GROUPS;

@RestController
@RequiredArgsConstructor
public class CarPoolingController {

  private final CarPoolingService carPoolingService;

  @GetMapping("/status")
  public Mono<String> status() {
    return Mono.just("I'm alive");
  }

  @PutMapping(path = "/cars", consumes = MediaType.APPLICATION_JSON_VALUE)
  public Flux<CarEntity> putCars(@RequestBody @Valid List<CarDTO> cars) {
    return carPoolingService.createCars(cars);
  }

  @PostMapping(path = "/journey", consumes = MediaType.APPLICATION_JSON_VALUE)
  public Mono<CarEntity> postJourney(@RequestBody @Valid GroupOfPeopleDTO group) {
    return carPoolingService.journey(group);
  }

  @PostMapping(path = "/dropoff", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public Mono<ResponseEntity<Void>> postDropoff(@Valid GroupOfPeopleForm group) {
    Integer id = group.getID();

    return carPoolingService.dropoff(id)
      .map(car -> {
        if (!WAITING_GROUPS.equals(car.getId())) {
          return new ResponseEntity<Void>(HttpStatus.OK);
        } else {
          return new ResponseEntity<Void>(HttpStatus.NO_CONTENT);
        }
      })
      .defaultIfEmpty(new ResponseEntity<>(HttpStatus.NOT_FOUND));
  }

  @PostMapping(path = "/locate", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<CarDTO>> postLocate(@Valid GroupOfPeopleForm group) {
    Integer groupId = group.getID();

    return carPoolingService.locateCarOfGroup(groupId)
      .map(car -> ResponseEntity.ok(new CarDTO(car.getId(), car.getSeatsAvailable())))
      .switchIfEmpty(carPoolingService.findWaitingGroup(groupId)
        .map(g -> new ResponseEntity<CarDTO>(HttpStatus.NO_CONTENT))
        .defaultIfEmpty(new ResponseEntity<>(HttpStatus.NOT_FOUND)));
  }
}
