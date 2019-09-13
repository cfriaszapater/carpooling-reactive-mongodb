package com.cabify.pooling.dto;

import javax.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GroupOfPeopleDTO {
	@NotNull
	private final Integer id;
	@NotNull
	private final Integer people;
}
