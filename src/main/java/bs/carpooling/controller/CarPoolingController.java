package bs.carpooling.controller;

import bs.carpooling.dto.CarDTO;
import bs.carpooling.dto.GroupOfPeopleDTO;
import bs.carpooling.dto.GroupOfPeopleForm;
import bs.carpooling.entity.CarEntity;
import bs.carpooling.service.CarPoolingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.validation.Valid;
import java.util.List;

import static bs.carpooling.repository.CarsRepository.WAITING_QUEUE;

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
        if (!WAITING_QUEUE.equals(car.getId())) {
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
