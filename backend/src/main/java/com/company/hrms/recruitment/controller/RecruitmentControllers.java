package com.company.hrms.recruitment.controller;

import com.company.hrms.recruitment.model.Interview;
import com.company.hrms.recruitment.model.JobApplicant;
import com.company.hrms.recruitment.model.JobOpening;
import com.company.hrms.recruitment.repository.InterviewRepo;
import com.company.hrms.recruitment.repository.JobApplicantRepo;
import com.company.hrms.recruitment.repository.JobOpeningRepo;

import java.time.LocalDate;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.hrms.common.CrudController;
import com.company.hrms.common.ApiException;
import com.company.hrms.employee.model.Employee;
import com.company.hrms.employee.service.EmployeeService;
import com.company.hrms.notification.service.NotificationService;

public class RecruitmentControllers {

    public record HireRequest(LocalDate dateOfJoining) {
    }

    @RestController
    @RequestMapping("/api/recruitment/openings")
    public static class Openings extends CrudController<JobOpening> {
        public Openings(JobOpeningRepo repo) {
            super(repo);
        }

        @Override
        protected JobOpening doCreate(JobOpening body) {
            if (body.getPostedOn() == null) {
                body.setPostedOn(LocalDate.now());
            }
            if (body.getStatus() == null) {
                body.setStatus(JobOpening.Status.OPEN);
            }
            return repo.save(body);
        }
    }

    @RestController
    @RequestMapping("/api/recruitment/applicants")
    public static class Applicants extends CrudController<JobApplicant> {

        private final EmployeeService employeeService;

        public Applicants(JobApplicantRepo repo, EmployeeService employeeService) {
            super(repo);
            this.employeeService = employeeService;
        }

        @Override
        protected JobApplicant doCreate(JobApplicant body) {
            if (body.getAppliedOn() == null) {
                body.setAppliedOn(LocalDate.now());
            }
            if (body.getStatus() == null) {
                body.setStatus(JobApplicant.Status.APPLIED);
            }
            return repo.save(body);
        }

        /** Turns the applicant into an employee record and marks the applicant as hired. */
        @PostMapping("/{id}/hire")
        public Employee hire(@PathVariable Long id, @RequestBody(required = false) HireRequest req) {
            JobApplicant applicant = load(id);
            if (applicant.getStatus() == JobApplicant.Status.HIRED) {
                throw ApiException.badRequest("This applicant is already hired");
            }
            String[] parts = applicant.getName().trim().split("\\s+", 2);
            Employee e = new Employee();
            e.setFirstName(parts[0]);
            e.setLastName(parts.length > 1 ? parts[1] : null);
            e.setEmail(applicant.getEmail());
            e.setPhone(applicant.getPhone());
            e.setDateOfJoining(req != null && req.dateOfJoining() != null ? req.dateOfJoining() : LocalDate.now());
            e.setStatus(Employee.Status.ACTIVE);
            e.setEmploymentType(Employee.EmploymentType.FULL_TIME);
            if (applicant.getJobOpening() != null) {
                e.setDepartment(applicant.getJobOpening().getDepartment());
                e.setDesignation(applicant.getJobOpening().getDesignation());
            }
            e = employeeService.create(e);
            applicant.setStatus(JobApplicant.Status.HIRED);
            repo.save(applicant);
            return e;
        }
    }

    @RestController
    @RequestMapping("/api/recruitment/interviews")
    public static class Interviews extends CrudController<Interview> {

        private final NotificationService notifications;
        private final EmployeeService employeeService;
        private final JobApplicantRepo applicants;

        public Interviews(InterviewRepo repo, NotificationService notifications,
                          EmployeeService employeeService, JobApplicantRepo applicants) {
            super(repo);
            this.notifications = notifications;
            this.employeeService = employeeService;
            this.applicants = applicants;
        }

        @Override
        protected Interview doCreate(Interview body) {
            if (body.getStatus() == null) {
                body.setStatus(Interview.Status.SCHEDULED);
            }
            Interview saved = repo.save(body);
            if (saved.getInterviewer() != null && saved.getInterviewer().getId() != null) {
                String applicantName = applicants.findById(saved.getApplicant().getId())
                        .map(JobApplicant::getName).orElse("an applicant");
                notifications.notifyEmployee(employeeService.get(saved.getInterviewer().getId()),
                        "Interview scheduled",
                        "You are the interviewer for " + applicantName
                                + (saved.getScheduledAt() == null ? "." : " on " + saved.getScheduledAt() + "."));
            }
            return saved;
        }
    }
}
