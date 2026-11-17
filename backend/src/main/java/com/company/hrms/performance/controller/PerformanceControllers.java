package com.company.hrms.performance.controller;

import com.company.hrms.performance.model.Appraisal;
import com.company.hrms.performance.model.AppraisalCycle;
import com.company.hrms.performance.model.Goal;
import com.company.hrms.performance.repository.AppraisalCycleRepo;
import com.company.hrms.performance.repository.AppraisalRepo;
import com.company.hrms.performance.repository.GoalRepo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.hrms.common.CrudController;
import com.company.hrms.common.ApiException;
import com.company.hrms.notification.service.NotificationService;

public class PerformanceControllers {

    @RestController
    @RequestMapping("/api/performance/cycles")
    public static class Cycles extends CrudController<AppraisalCycle> {
        public Cycles(AppraisalCycleRepo repo) {
            super(repo);
        }
    }

    @RestController
    @RequestMapping("/api/performance/goals")
    public static class Goals extends CrudController<Goal> {
        public Goals(GoalRepo repo) {
            super(repo);
        }

        @Override
        protected Goal doCreate(Goal body) {
            check(body);
            return repo.save(body);
        }

        @Override
        protected Goal doUpdate(Goal existing, Goal body) {
            check(body);
            return repo.save(body);
        }

        private static void check(Goal g) {
            if (g.getProgress() == null) {
                g.setProgress(0);
            }
            if (g.getProgress() < 0 || g.getProgress() > 100) {
                throw ApiException.badRequest("Progress must be between 0 and 100");
            }
            if (g.getStatus() == null) {
                g.setStatus(Goal.Status.OPEN);
            }
        }
    }

    @RestController
    @RequestMapping("/api/performance/appraisals")
    public static class Appraisals extends CrudController<Appraisal> {

        private static final BigDecimal ONE = BigDecimal.ONE;
        private static final BigDecimal FIVE = BigDecimal.valueOf(5);

        private final GoalRepo goals;
        private final NotificationService notifications;

        public Appraisals(AppraisalRepo repo, GoalRepo goals,
                          NotificationService notifications) {
            super(repo);
            this.goals = goals;
            this.notifications = notifications;
        }

        @Override
        protected Appraisal doCreate(Appraisal body) {
            check(body);
            body.setStatus(Appraisal.Status.DRAFT);
            body.setFinalScore(null);
            return repo.save(body);
        }

        @Override
        protected Appraisal doUpdate(Appraisal existing, Appraisal body) {
            if (existing.getStatus() == Appraisal.Status.FINALIZED) {
                throw ApiException.badRequest("A finalized appraisal cannot be changed");
            }
            check(body);
            body.setFinalScore(null);
            if (body.getManagerRating() != null) {
                body.setStatus(Appraisal.Status.MANAGER_REVIEWED);
            } else if (body.getSelfRating() != null) {
                body.setStatus(Appraisal.Status.SELF_REVIEWED);
            } else {
                body.setStatus(Appraisal.Status.DRAFT);
            }
            return repo.save(body);
        }

        /**
         * Final score = 70% manager rating + 30% goal achievement (average goal progress in this cycle,
         * weighted by goal weightage, scaled to 5). With no goals the manager rating is used as is.
         */
        @PostMapping("/{id}/submit")
        public Appraisal finalizeAppraisal(@PathVariable Long id) {
            Appraisal a = load(id);
            if (a.getStatus() == Appraisal.Status.FINALIZED) {
                throw ApiException.badRequest("This appraisal is already finalized");
            }
            if (a.getManagerRating() == null) {
                throw ApiException.badRequest("Enter the manager rating before finalizing");
            }
            List<Goal> cycleGoals = goals.findByEmployeeIdOrderByIdDesc(a.getEmployee().getId()).stream()
                    .filter(g -> g.getCycle() != null && g.getCycle().getId().equals(a.getCycle().getId()))
                    .filter(g -> g.getStatus() != Goal.Status.DROPPED)
                    .toList();
            BigDecimal score = a.getManagerRating();
            if (!cycleGoals.isEmpty()) {
                BigDecimal weightSum = BigDecimal.ZERO;
                BigDecimal weighted = BigDecimal.ZERO;
                for (Goal g : cycleGoals) {
                    BigDecimal w = g.getWeightage() == null || g.getWeightage().signum() <= 0 ? ONE : g.getWeightage();
                    weightSum = weightSum.add(w);
                    weighted = weighted.add(w.multiply(BigDecimal.valueOf(g.getProgress() == null ? 0 : g.getProgress())));
                }
                // average progress (0-100) -> 0-5 scale
                BigDecimal goalScore = weighted.divide(weightSum, 6, RoundingMode.HALF_UP)
                        .multiply(FIVE).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
                score = a.getManagerRating().multiply(new BigDecimal("0.7")).add(goalScore.multiply(new BigDecimal("0.3")));
            }
            a.setFinalScore(score.setScale(1, RoundingMode.HALF_UP));
            a.setStatus(Appraisal.Status.FINALIZED);
            Appraisal saved = repo.save(a);
            notifications.notifyEmployee(saved.getEmployee(), "Appraisal finalized",
                    "Your appraisal for " + saved.getCycle().getName() + " is finalized. Final score: "
                            + saved.getFinalScore().toPlainString() + " out of 5.");
            return saved;
        }

        private static void check(Appraisal a) {
            for (BigDecimal r : new BigDecimal[] {a.getSelfRating(), a.getManagerRating()}) {
                if (r != null && (r.compareTo(ONE) < 0 || r.compareTo(FIVE) > 0)) {
                    throw ApiException.badRequest("Ratings must be between 1 and 5");
                }
            }
        }
    }
}
