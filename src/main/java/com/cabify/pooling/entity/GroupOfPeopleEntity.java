package com.cabify.pooling.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class GroupOfPeopleEntity {
	@EqualsAndHashCode.Include
	private final Integer id;
	private final Integer people;
	private CarEntity car;
}
