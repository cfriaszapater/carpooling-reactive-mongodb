package bs.carpooling.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
@AllArgsConstructor
public class GroupOfPeopleDTO {
  @NotNull
  private Integer id;
  @NotNull
  private Integer people;
}
