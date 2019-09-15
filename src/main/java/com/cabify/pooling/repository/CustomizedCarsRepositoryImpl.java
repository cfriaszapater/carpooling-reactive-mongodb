package com.cabify.pooling.repository;

import static org.springframework.data.domain.Sort.by;
import static org.springframework.data.domain.Sort.Order.asc;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.cabify.pooling.entity.CarEntity;
import com.cabify.pooling.entity.GroupOfPeopleEntity;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class CustomizedCarsRepositoryImpl implements CustomizedCarsRepository {

	private static final String SEATS_AVAILABLE = "seatsAvailable";
	
	private final @NonNull ReactiveMongoOperations mongoOperations;
	
	public Mono<CarEntity> assignToCarWithAvailableSeats(GroupOfPeopleEntity group) {
		Query query = Query.query(Criteria.where(SEATS_AVAILABLE).gte(group.getPeople())).with(by(asc(SEATS_AVAILABLE)));
		Update update = new Update()
				.inc(SEATS_AVAILABLE, -group.getPeople())
				.addToSet("groups").value(group);
		return mongoOperations.findAndModify(query, update, new FindAndModifyOptions().returnNew(true), CarEntity.class);
	}

	@Override
	public Mono<CarEntity> removeGroupFromCarAndFreeSeats(Integer groupId) {
		Query query = Query.query(Criteria.where("groups.id").is(groupId));
		Mono<GroupOfPeopleEntity> groupToRemove = mongoOperations.findOne(query, CarEntity.class)
				.map(car -> car.getGroups())
				.flatMapMany(Flux::fromIterable)
				.filter(group -> group.getId().equals(groupId)).next();

		return groupToRemove.flatMap(group -> {
			Update update = new Update().inc(SEATS_AVAILABLE, group.getPeople()).addToSet("groups").value(group);
			return mongoOperations.findAndModify(query, update, new FindAndModifyOptions().returnNew(true), CarEntity.class);
		});
	}
}
