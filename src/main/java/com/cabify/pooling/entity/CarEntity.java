package com.cabify.pooling.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Document
public class CarEntity {
  @EqualsAndHashCode.Include
  @Id
  private Integer id;
  private Integer seatsAvailable;
  private List<GroupOfPeopleEntity> groups;
  private Date reassigningSince;
  @Version
  private Long version;
}
