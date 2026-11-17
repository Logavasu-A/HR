package com.company.hrms.recruitment.repository;

import com.company.hrms.recruitment.model.Interview;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface InterviewRepo extends JpaRepository<Interview, Long> {
}
