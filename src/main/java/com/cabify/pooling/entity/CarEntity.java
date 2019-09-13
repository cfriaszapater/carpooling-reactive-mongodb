package com.cabify.pooling.entity;

import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CarEntity {
	@EqualsAndHashCode.Include
	private Integer id;
	private Integer seatsAvailable;
	private Set<GroupOfPeopleEntity> groups;
}
