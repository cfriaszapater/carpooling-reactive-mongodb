package com.cabify.pooling.dto;

import javax.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GroupOfPeopleDTO {
	@NotNull
	private Integer id;
	@NotNull
	private Integer people;
}
