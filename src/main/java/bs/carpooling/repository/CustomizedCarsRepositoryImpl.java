package bs.carpooling.repository;

import bs.carpooling.entity.CarEntity;
import bs.carpooling.entity.GroupOfPeopleEntity;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static bs.carpooling.repository.CarsRepository.WAITING_GROUPS;
import static org.springframework.data.domain.Sort.Order.asc;
import static org.springframework.data.domain.Sort.by;
import static org.springframework.data.mongodb.core.query.Query.query;

@RequiredArgsConstructor
@Slf4j
public class CustomizedCarsRepositoryImpl implements CustomizedCarsRepository {

  private static final String SEATS_AVAILABLE = "seatsAvailable";
  private static final String GROUPS = "groups";

  private final @NonNull ReactiveMongoOperations mongoOperations;

  @Override
  public Mono<CarEntity> assignToCarWithAvailableSeats(GroupOfPeopleEntity group) {
    int people = group.getPeople();
    Update update = new Update()
      .inc(SEATS_AVAILABLE, -people)
      .addToSet(GROUPS).value(group);
    return mongoOperations.findAndModify(queryBySeatsAvailable(people), update, new FindAndModifyOptions().returnNew(true), CarEntity.class);
  }

  private Query queryBySeatsAvailable(int people) {
    return query(Criteria.where(SEATS_AVAILABLE).gte(people)).with(by(asc(SEATS_AVAILABLE)));
  }

  @Override
  public Mono<GroupOfPeopleEntity> locateGroupById(Integer groupId) {
    return locateCarOfGroup(groupId)
      .flatMapMany(car -> Flux.fromIterable(car.getGroups()))
      .filter(group -> group.getId().equals(groupId))
      .next()
      ;
  }

  @Override
  public Mono<CarEntity> locateCarOfGroup(Integer groupId) {
    return mongoOperations.findOne(queryByGroupId(groupId), CarEntity.class);
  }

  private Query queryByGroupId(Integer groupId) {
    return query(Criteria
      .where("groups.id").is(groupId)
      .and("id").ne(WAITING_GROUPS));
  }

  @Override
  public Mono<CarEntity> putInWaitingQueue(GroupOfPeopleEntity group) {
    Update update = new Update().addToSet(GROUPS).value(group);
    return mongoOperations.findAndModify(queryWaitingGroups(), update, new FindAndModifyOptions().returnNew(true), CarEntity.class);
  }

  private Query queryWaitingGroups() {
    return query(Criteria
      .where("id").is(WAITING_GROUPS));
  }

  @Override
  public Mono<GroupOfPeopleEntity> findWaitingById(Integer groupId) {
    return waitingGroups()
      .filter(group -> group.getId().equals(groupId))
      .next()
      ;
  }

  @Override
  public Flux<GroupOfPeopleEntity> findAllGroupsWaiting() {
    return waitingGroups();
  }

  private Flux<GroupOfPeopleEntity> waitingGroups() {
    return mongoOperations.findOne(queryWaitingGroups(), CarEntity.class)
      .flatMapMany(car -> Flux.fromIterable(car.getGroups()));
  }

  @Override
  public Flux<CarEntity> findAllNotWaiting() {
    return mongoOperations.find(query(Criteria.where("id").ne(WAITING_GROUPS)), CarEntity.class);
  }

  @Override
  public Flux<GroupOfPeopleEntity> reassignOneWaitingGroup() {
    // Thread-safety: optimistic locking with transaction, does rollback in case of failure

    return firstWaitingGroup()
      .flatMapMany(this::reassign)
      // retry failed optimistic concurrent executions of reassignOneWaitingGroup
      .retryBackoff(3, Duration.ofMillis(200))
      ;
  }

  private Mono<GroupOfPeopleEntity> firstWaitingGroup() {
    return mongoOperations.findOne(queryWaitingGroups(), CarEntity.class)
      .flatMap(car -> Mono.justOrEmpty(car.getGroups().stream().findFirst()));
  }

  private Flux<GroupOfPeopleEntity> reassign(GroupOfPeopleEntity waitingGroup) {
    int people = waitingGroup.getPeople();
    Update update = new Update()
      .inc(SEATS_AVAILABLE, -people)
      .addToSet(GROUPS).value(waitingGroup);
    Update remove = new Update().pull(GROUPS, waitingGroup);
    return mongoOperations.inTransaction()
      .execute(action ->
        action.findAndModify(queryBySeatsAvailable(people), update, new FindAndModifyOptions().returnNew(true), CarEntity.class)
          .flatMap(car -> action.findAndModify(queryWaitingGroup(waitingGroup.getId()), remove, new FindAndModifyOptions().returnNew(true), CarEntity.class)
            .switchIfEmpty(Mono.error(new RuntimeException("Waiting group not found on reassigning it, rolling back transaction"))))
          .flatMap(car -> Mono.just(waitingGroup))
      );
  }

  @Override
  public Mono<CarEntity> dropoff(Integer groupId) {
    Mono<GroupOfPeopleEntity> groupToRemove = findGroupById(groupId);

    return groupToRemove.flatMap(group -> {
      Update leaveWaitingQueue = new Update().pull(GROUPS, group);
      Update leaveCar = new Update().inc(SEATS_AVAILABLE, group.getPeople()).pull(GROUPS, group);
      return mongoOperations.findAndModify(queryWaitingGroup(groupId), leaveWaitingQueue, new FindAndModifyOptions().returnNew(true), CarEntity.class)
        .switchIfEmpty(
          mongoOperations.findAndModify(queryByGroupId(groupId), leaveCar, new FindAndModifyOptions().returnNew(true), CarEntity.class)
        );
    });
  }

  private Query queryWaitingGroup(Integer waitingGroupId) {
    return query(Criteria
      .where("id").is(WAITING_GROUPS)
      .and("groups.id").is(waitingGroupId));
  }

  private Mono<GroupOfPeopleEntity> findGroupById(Integer groupId) {
    return waitingGroups()
      .filter(group -> group.getId().equals(groupId))
      .next()
      .switchIfEmpty(locateGroupById(groupId))
      ;
  }

}
