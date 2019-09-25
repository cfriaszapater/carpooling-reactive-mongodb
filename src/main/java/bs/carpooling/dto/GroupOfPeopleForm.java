package bs.carpooling.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class GroupOfPeopleForm {
  @NotNull
  private Integer ID;
}
