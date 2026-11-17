package com.company.hrms.modules.attendance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.company.hrms.modules.attendance.entity.ShiftType;

@Repository
public interface ShiftTypeRepo extends JpaRepository<ShiftType, Long> {
}
