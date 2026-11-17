package modules.recruitment.repository;

import modules.recruitment.model.Interview;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InterviewRepo extends JpaRepository<Interview, Long> {
}
