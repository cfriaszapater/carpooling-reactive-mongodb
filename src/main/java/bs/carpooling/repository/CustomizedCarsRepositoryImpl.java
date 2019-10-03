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

import static bs.carpooling.repository.CarsRepository.WAITING_QUEUE;
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
    return groupEntersCarWithSeatsAvailable(group, mongoOperations);
  }

  private Mono<CarEntity> groupEntersCarWithSeatsAvailable(GroupOfPeopleEntity group, ReactiveMongoOperations action) {
    return action
      .findAndModify(carWithSeatsAvailable(group.getPeople()), enterCar(group), new FindAndModifyOptions().returnNew(true), CarEntity.class);
  }

  private Query carWithSeatsAvailable(int people) {
    return query(Criteria.where(SEATS_AVAILABLE).gte(people)).with(by(asc(SEATS_AVAILABLE)));
  }

  private Update enterCar(GroupOfPeopleEntity waitingGroup) {
    return new Update()
      .inc(SEATS_AVAILABLE, -waitingGroup.getPeople())
      .addToSet(GROUPS).value(waitingGroup);
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
    return mongoOperations.findOne(groupById(groupId), CarEntity.class);
  }

  private Query groupById(Integer groupId) {
    return query(Criteria
      .where("groups.id").is(groupId)
      .and("id").ne(WAITING_QUEUE));
  }

  @Override
  public Mono<CarEntity> putInWaitingQueue(GroupOfPeopleEntity group) {
    Update enterWaitingQueue = new Update().addToSet(GROUPS).value(group);
    return mongoOperations.findAndModify(waitingQueue(), enterWaitingQueue, new FindAndModifyOptions().returnNew(true), CarEntity.class);
  }

  private Query waitingQueue() {
    return query(Criteria
      .where("id").is(WAITING_QUEUE));
  }

  @Override
  public Mono<GroupOfPeopleEntity> findWaitingById(Integer groupId) {
    return findWaitingGroups()
      .filter(group -> group.getId().equals(groupId))
      .next()
      ;
  }

  @Override
  public Flux<GroupOfPeopleEntity> findAllGroupsWaiting() {
    return findWaitingGroups();
  }

  private Flux<GroupOfPeopleEntity> findWaitingGroups() {
    return findWaitingQueue()
      .flatMapMany(car -> Flux.fromIterable(car.getGroups()));
  }

  private Mono<CarEntity> findWaitingQueue() {
    return mongoOperations.findOne(waitingQueue(), CarEntity.class);
  }

  @Override
  public Flux<CarEntity> findAllNotWaiting() {
    return mongoOperations.find(query(Criteria.where("id").ne(WAITING_QUEUE)), CarEntity.class);
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
    return findWaitingQueue()
      .flatMap(car -> Mono.justOrEmpty(car.getGroups().stream().findFirst()));
  }

  private Flux<GroupOfPeopleEntity> reassign(GroupOfPeopleEntity waitingGroup) {
    // Thread-safety: transaction needed to atomically update two documents (waitingQueue and carWithSeatsAvailable)

    return mongoOperations.inTransaction()
      .execute(action -> groupEntersCarWithSeatsAvailable(waitingGroup, action)
        .flatMap(car -> groupLeavesWaitingQueue(waitingGroup, action)
          .switchIfEmpty(Mono.error(new RuntimeException("Waiting group not found on reassigning it, rolling back transaction"))))
        .flatMap(car -> Mono.just(waitingGroup))
      );
  }

  private Mono<CarEntity> groupLeavesWaitingQueue(GroupOfPeopleEntity group, ReactiveMongoOperations action) {
    return action
      .findAndModify(waitingGroup(group.getId()), leaveWaitingQueue(group), new FindAndModifyOptions().returnNew(true), CarEntity.class);
  }

  private Query waitingGroup(Integer waitingGroupId) {
    return query(Criteria
      .where("id").is(WAITING_QUEUE)
      .and("groups.id").is(waitingGroupId));
  }

  private Update leaveWaitingQueue(GroupOfPeopleEntity waitingGroup) {
    return new Update().pull(GROUPS, waitingGroup);
  }

  @Override
  public Mono<CarEntity> dropoff(Integer groupId) {
    Mono<GroupOfPeopleEntity> groupToRemove = findGroupById(groupId);

    return groupToRemove
      .flatMap(group ->
        groupLeavesWaitingQueue(group, mongoOperations)
          .switchIfEmpty(groupLeavesCar(group))
      );
  }

  private Mono<GroupOfPeopleEntity> findGroupById(Integer groupId) {
    return findWaitingGroups()
      .filter(group -> group.getId().equals(groupId))
      .next()
      .switchIfEmpty(locateGroupById(groupId))
      ;
  }

  private Mono<CarEntity> groupLeavesCar(GroupOfPeopleEntity group) {
    return mongoOperations
      .findAndModify(groupById(group.getId()), leaveCar(group), new FindAndModifyOptions().returnNew(true), CarEntity.class);
  }

  private Update leaveCar(GroupOfPeopleEntity group) {
    return new Update().inc(SEATS_AVAILABLE, group.getPeople()).pull(GROUPS, group);
  }

}
