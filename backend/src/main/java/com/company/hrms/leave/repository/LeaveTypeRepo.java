package com.company.hrms.modules.leave.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.company.hrms.modules.leave.entity.LeaveType;

@Repository
public interface LeaveTypeRepo extends JpaRepository<LeaveType, Long> {
}
