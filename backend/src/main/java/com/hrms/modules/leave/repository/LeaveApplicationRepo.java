package com.hrms.modules.leave.repository;

import com.hrms.modules.leave.model.LeaveApplication;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hrms.shared.common.ApprovalStatus;

@Repository
public interface LeaveApplicationRepo extends JpaRepository<LeaveApplication, Long> {
    List<LeaveApplication> findByEmployeeIdOrderByFromDateDesc(Long employeeId);

    Page<LeaveApplication> findByEmployeeIdOrderByFromDateDesc(Long employeeId, Pageable pageable);

    List<LeaveApplication> findByEmployeeIdAndStatusIn(Long employeeId, Collection<ApprovalStatus> statuses);

    /** Applications with the given status that overlap the date range. */
    List<LeaveApplication> findByStatusAndFromDateLessThanEqualAndToDateGreaterThanEqual(
            ApprovalStatus status, LocalDate rangeEnd, LocalDate rangeStart);

    List<LeaveApplication> findByStatusAndFromDateGreaterThanEqual(ApprovalStatus status, LocalDate fromDate);

    long countByStatus(ApprovalStatus status);
}
