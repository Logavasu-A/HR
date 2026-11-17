package com.company.hrms.organization.repository;

import com.company.hrms.organization.model.Branch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface BranchRepo extends JpaRepository<Branch, Long> {
}
