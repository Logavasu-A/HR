package com.company.hrms.leave.repository;

import com.company.hrms.leave.model.LeaveType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface LeaveTypeRepo extends JpaRepository<LeaveType, Long> {
}
