package com.hrms.modules.leave.repository;

import com.hrms.modules.leave.model.LeaveType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LeaveTypeRepo extends JpaRepository<LeaveType, Long> {
}
