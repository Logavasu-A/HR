package modules.recruitment.repository;

import modules.recruitment.model.JobOpening;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobOpeningRepo extends JpaRepository<JobOpening, Long> {
}
