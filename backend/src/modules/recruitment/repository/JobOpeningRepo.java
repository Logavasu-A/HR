package modules.recruitment.repository;

import modules.recruitment.model.JobOpening;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobOpeningRepo extends JpaRepository<JobOpening, Long> {
    List<JobOpening> findByStatus(JobOpening.Status status);
}
