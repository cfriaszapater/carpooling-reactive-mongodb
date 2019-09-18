package com.cabify.pooling.repository;

import com.cabify.pooling.entity.CarEntity;
import com.cabify.pooling.entity.GroupOfPeopleEntity;
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

import java.util.Date;

import static com.cabify.pooling.repository.CarsRepository.WAITING_GROUPS;
import static org.springframework.data.domain.Sort.Order.asc;
import static org.springframework.data.domain.Sort.by;

@RequiredArgsConstructor
@Slf4j
public class CustomizedCarsRepositoryImpl implements CustomizedCarsRepository {

	private static final String SEATS_AVAILABLE = "seatsAvailable";

	private final @NonNull ReactiveMongoOperations mongoOperations;

	@Override
	public Mono<CarEntity> assignToCarWithAvailableSeats(GroupOfPeopleEntity group) {
		int people = group.getPeople();
		Update update = new Update()
				.inc(SEATS_AVAILABLE, -people)
				.addToSet("groups").value(group);
		return mongoOperations.findAndModify(queryBySeatsAvailable(people), update, new FindAndModifyOptions().returnNew(true), CarEntity.class);
	}

	private Query queryBySeatsAvailable(int people) {
		return Query.query(Criteria.where(SEATS_AVAILABLE).gte(people)).with(by(asc(SEATS_AVAILABLE)));
	}

	@Override
	public Mono<CarEntity> removeGroupFromCarAndFreeSeats(Integer groupId) {
		Mono<GroupOfPeopleEntity> groupToRemove = locateGroupById(groupId);

		return groupToRemove.flatMap(group -> {
			Update update = new Update().inc(SEATS_AVAILABLE, group.getPeople()).pull("groups", group);
			return mongoOperations.findAndModify(queryByGroupId(groupId), update, new FindAndModifyOptions().returnNew(true), CarEntity.class);
		});
	}

	private Query queryByGroupId(Integer groupId) {
		return Query.query(Criteria
				.where("groups.id").is(groupId)
				.and("id").ne(WAITING_GROUPS));
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

	@Override
	public Mono<GroupOfPeopleEntity> putInWaitingQueue(GroupOfPeopleEntity group) {
		Update update = new Update().addToSet("groups").value(group);
		return mongoOperations.findAndModify(queryWaitingGroups(), update, new FindAndModifyOptions().returnNew(true), CarEntity.class)
				.then(Mono.just(group));
	}

	private Query queryWaitingGroups() {
		return Query.query(Criteria
				.where("id").is(WAITING_GROUPS));
	}

	@Override
	public Flux<GroupOfPeopleEntity> findWaitingAndSetReassigning() {
		Query waitingNotReassigning = Query.query(
				Criteria.where("id").is(WAITING_GROUPS)
						.and("reassigningSince").is(null));
		Update setReassigningSince = new Update().set("reassigningSince", new Date());
		return mongoOperations
				.findAndModify(waitingNotReassigning, setReassigningSince, new FindAndModifyOptions().returnNew(true), CarEntity.class)
				.flatMapMany(car -> Flux.fromIterable(car.getGroups()));
	}

	@Override
	public Mono<CarEntity> findReassigningAndUnset() {
		Query reassigning = Query.query(
				Criteria.where("id").is(WAITING_GROUPS)
						.and("reassigningSince").ne(null));
		Update unsetReassigningSince = new Update().set("reassigningSince", null);
		return mongoOperations
				.findAndModify(reassigning, unsetReassigningSince, new FindAndModifyOptions().returnNew(true), CarEntity.class);
	}

	@Override
	public Mono<CarEntity> findWaitingReassigningByIdAndDelete(GroupOfPeopleEntity group) {
		Update removeGroup = new Update()
				.pull("groups", group);
		return mongoOperations
				.findAndModify(queryByIdReassigning(group), removeGroup, new FindAndModifyOptions().returnNew(true), CarEntity.class);
	}

	private Query queryByIdReassigning(GroupOfPeopleEntity group) {
		return Query.query(Criteria
				.where("id").is(WAITING_GROUPS)
				.and("reassigningSince").ne(null)
				.and("groups.id").is(group.getId()));
	}

	@Override
	public Mono<CarEntity> findWaitingReassigningByIdAndUnset(GroupOfPeopleEntity group) {
		Update unsetReassigning = new Update()
				.set("reassigning", false);
		return mongoOperations
				.findAndModify(queryByIdReassigning(group), unsetReassigning, new FindAndModifyOptions().returnNew(true), CarEntity.class);
	}

	@Override
	public Mono<GroupOfPeopleEntity> findWaitingById(Integer groupId) {
		return waitingGroups()
				.filter(group -> group.getId().equals(groupId))
				.next()
				;
	}

	@Override
	public Mono<CarEntity> deleteWaitingById(Integer groupId) {
		Mono<GroupOfPeopleEntity> groupToRemove = locateGroupById(groupId);

		return groupToRemove.flatMap(group -> {
			Update update = new Update().pull("groups", group);
			return mongoOperations.findAndModify(queryByGroupId(groupId), update, new FindAndModifyOptions().returnNew(true), CarEntity.class);
		});
	}

	@Override
	public Flux<GroupOfPeopleEntity> findAllWaiting() {
		return waitingGroups();
	}

	private Flux<GroupOfPeopleEntity> waitingGroups() {
		return mongoOperations.findOne(queryWaitingGroups(), CarEntity.class)
				.flatMapMany(car -> Flux.fromIterable(car.getGroups()));
	}
}
