package com.cabify.pooling.entity;

import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CarEntity {
	@EqualsAndHashCode.Include
	private final int id;
	private final int seatsAvailable;
	private Set<GroupOfPeopleEntity> groups;
}
