package com.company.hrms.modules.leave.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.company.hrms.modules.leave.entity.HolidayList;

@Repository
public interface HolidayListRepo extends JpaRepository<HolidayList, Long> {
    Optional<HolidayList> findFirstByDefaultListTrue();
}
