package com.company.hrms.modules.attendance.entity;

import java.time.LocalTime;

import com.company.hrms.common.base.BaseEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "shift_type")
@Getter
@Setter
public class ShiftType extends BaseEntity {

    @NotBlank
    private String name;

    private LocalTime startTime;

    private LocalTime endTime;

    private Integer graceMinutes;
}
