//package com.cabify.pooling.repository;
//
//import com.cabify.pooling.entity.GroupOfPeopleEntity;
//import lombok.NonNull;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.data.mongodb.core.FindAndModifyOptions;
//import org.springframework.data.mongodb.core.ReactiveMongoOperations;
//import org.springframework.data.mongodb.core.query.Criteria;
//import org.springframework.data.mongodb.core.query.Query;
//import org.springframework.data.mongodb.core.query.Update;
//import reactor.core.publisher.Mono;
//
//import java.util.Date;
//
//import static org.springframework.data.domain.Sort.Order.asc;
//import static org.springframework.data.domain.Sort.by;
//
//@RequiredArgsConstructor
//@Slf4j
//public class CustomizedGroupsRepositoryImpl implements CustomizedGroupsRepository {
//
//	private final @NonNull ReactiveMongoOperations mongoOperations;
//
//	@Override
//	public Mono<GroupOfPeopleEntity> findOneAndSetReassigning() {
//		Query firstInWaitingQueue = Query.query(Criteria.where("reassigningSince").is(null)).limit(1).with(by(asc("insertDate")));
//		Update setReassigningSince = new Update().set("reassigningSince", new Date());
//		return mongoOperations.findAndModify(firstInWaitingQueue, setReassigningSince, new FindAndModifyOptions().returnNew(true), GroupOfPeopleEntity.class);
//	}
//
//	@Override
//	public Mono<GroupOfPeopleEntity> findByIdAndUnsetReassigning(Integer groupId) {
//		Query byId = Query.query(Criteria.where("id").is(groupId).and("reassigningSince").not().is(null));
//		Update unsetReassigningSince = new Update().set("reassigningSince", null);
//		return mongoOperations.findAndModify(byId, unsetReassigningSince, new FindAndModifyOptions().returnNew(true), GroupOfPeopleEntity.class);
//	}
//}
