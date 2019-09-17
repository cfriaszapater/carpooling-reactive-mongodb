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

import static org.springframework.data.domain.Sort.Order.asc;
import static org.springframework.data.domain.Sort.by;

@RequiredArgsConstructor
@Slf4j
public class CustomizedCarsRepositoryImpl implements CustomizedCarsRepository {

	private static final String SEATS_AVAILABLE = "seatsAvailable";

	private final @NonNull ReactiveMongoOperations mongoOperations;

	@Override
	public Mono<CarEntity> assignToCarWithAvailableSeats(GroupOfPeopleEntity group) {
		Query query = Query.query(Criteria.where(SEATS_AVAILABLE).gte(group.getPeople())).with(by(asc(SEATS_AVAILABLE)));
		Update update = new Update()
				.inc(SEATS_AVAILABLE, -group.getPeople())
				.addToSet("groups").value(group);
		return mongoOperations.findAndModify(query, update, new FindAndModifyOptions().returnNew(true), CarEntity.class);
	}

	@Override
	public Mono<CarEntity> removeGroupFromCarAndFreeSeats(Integer groupId) {
		Mono<GroupOfPeopleEntity> groupToRemove = locateGroupById(groupId);

		return groupToRemove.flatMap(group -> {
			Update update = new Update().inc(SEATS_AVAILABLE, group.getPeople()).pull("groups", group);
			return mongoOperations.findAndModify(queryByGroupId(groupId), update, new FindAndModifyOptions().returnNew(true), CarEntity.class);
		});
	}

	@Override
	public Mono<GroupOfPeopleEntity> locateGroupById(Integer groupId) {
		return locateCarOfGroup(groupId)
				.map(car -> car.getGroups())
				.flatMapMany(Flux::fromIterable)
				.filter(group -> group.getId().equals(groupId)).next();
	}

	@Override
	public Mono<CarEntity> locateCarOfGroup(Integer groupId) {
		return mongoOperations.findOne(queryByGroupId(groupId), CarEntity.class);
	}

	private Query queryByGroupId(Integer groupId) {
		return Query.query(Criteria.where("groups.id").is(groupId));
	}
}
