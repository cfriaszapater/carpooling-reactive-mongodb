package com.cabify.pooling.dto;

import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
public class GroupOfPeopleForm {
	@NotNull
	private Integer ID;
}
