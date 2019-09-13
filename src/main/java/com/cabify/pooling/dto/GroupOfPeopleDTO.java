package com.cabify.pooling.dto;

import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
public class GroupOfPeopleDTO {
	@NotNull
	private Integer id;
	@NotNull
	private Integer people;
}
