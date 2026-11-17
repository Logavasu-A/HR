package com.company.hrms.attendance.repository;

import com.company.hrms.attendance.model.ShiftType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface ShiftTypeRepo extends JpaRepository<ShiftType, Long> {
}
