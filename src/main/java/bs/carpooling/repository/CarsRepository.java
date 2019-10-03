package bs.carpooling.repository;

import bs.carpooling.entity.CarEntity;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

import java.util.Collections;

public interface CarsRepository extends ReactiveMongoRepository<CarEntity, Integer>, CustomizedCarsRepository {
  Integer WAITING_QUEUE = -1;

  default Flux<CarEntity> initWith(Flux<CarEntity> carEntities) {
    return deleteAll()
      .thenMany(saveAll(waitingQueueContainer()
        .concatWith(carEntities)));
  }

  // A special "car" that holds the waiting groups, to enable modifying it atomically
  // (see https://docs.mongodb.com/manual/core/transactions/ )
  private Flux<CarEntity> waitingQueueContainer() {
    return Flux.just(new CarEntity(WAITING_QUEUE, 0, Collections.emptyList(), null, null));
  }
}
