package modules.organization.repository;

import modules.organization.model.Designation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DesignationRepo extends JpaRepository<Designation, Long> {
}
