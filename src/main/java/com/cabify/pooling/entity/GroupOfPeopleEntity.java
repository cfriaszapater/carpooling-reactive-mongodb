package com.cabify.pooling.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class GroupOfPeopleEntity {
	@EqualsAndHashCode.Include
	private final Integer id;
	private final Integer people;
}
