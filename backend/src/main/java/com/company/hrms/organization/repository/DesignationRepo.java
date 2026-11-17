package com.company.hrms.modules.organization.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.company.hrms.modules.organization.entity.Designation;

@Repository
public interface DesignationRepo extends JpaRepository<Designation, Long> {
}
