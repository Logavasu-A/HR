package com.company.hrms.leave.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.company.hrms.common.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "holiday_list")
@Getter
@Setter
public class HolidayList extends BaseEntity {

    @NotBlank
    private String name;

    @NotNull
    private LocalDate fromDate;

    @NotNull
    private LocalDate toDate;

    /** Comma-separated weekdays that are always off, e.g. "SATURDAY,SUNDAY". */
    private String weeklyOffs;

    /** Used for employees that have no holiday list of their own. */
    private boolean defaultList;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "holiday_list_id")
    @OrderBy("holidayDate")
    private List<Holiday> holidays = new ArrayList<>();
}
