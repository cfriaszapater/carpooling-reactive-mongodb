package com.cabify.pooling.entity;

import lombok.*;

import java.util.Date;
import java.util.List;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CarEntity {
	@EqualsAndHashCode.Include
	private Integer id;
	private Integer seatsAvailable;
	private List<GroupOfPeopleEntity> groups;
	private Date reassigningSince;
}
