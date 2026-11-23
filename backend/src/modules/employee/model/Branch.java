package modules.employee.model;

import shared.common.BaseEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "branch")
@Getter
@Setter
public class Branch extends BaseEntity {

    @NotBlank
    private String name;

    private String description;
}
