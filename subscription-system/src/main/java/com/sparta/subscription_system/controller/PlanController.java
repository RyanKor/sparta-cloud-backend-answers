package com.sparta.subscription_system.controller;

import com.sparta.subscription_system.dto.CreatePlanRequest;
import com.sparta.subscription_system.entity.Plan;
import com.sparta.subscription_system.repository.PlanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/plans")
@CrossOrigin(origins = "*")
public class PlanController {

    @Autowired
    private PlanRepository planRepository;

    @PostMapping
    public ResponseEntity<Plan> createPlan(@RequestBody CreatePlanRequest request) {
        Plan plan = new Plan();
        plan.setName(request.getName());
        plan.setDescription(request.getDescription());
        plan.setPrice(request.getPrice());
        plan.setBillingInterval(request.getBillingInterval());
        plan.setTrialPeriodDays(request.getTrialPeriodDays() != null ? request.getTrialPeriodDays() : 0);
        plan.setStatus(Plan.PlanStatus.ACTIVE);

        Plan savedPlan = planRepository.save(plan);
        return ResponseEntity.ok(savedPlan);
    }

    @GetMapping
    public ResponseEntity<List<Plan>> getAllPlans() {
        List<Plan> plans = planRepository.findAll();
        return ResponseEntity.ok(plans);
    }

    @GetMapping("/active")
    public ResponseEntity<List<Plan>> getActivePlans() {
        List<Plan> plans = planRepository.findByStatus(Plan.PlanStatus.ACTIVE);
        return ResponseEntity.ok(plans);
    }

    @GetMapping("/{planId}")
    public ResponseEntity<Plan> getPlanById(@PathVariable Long planId) {
        Optional<Plan> plan = planRepository.findById(planId);
        return plan.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{planId}/status")
    public ResponseEntity<Plan> updatePlanStatus(@PathVariable Long planId,
                                                 @RequestParam Plan.PlanStatus status) {
        Optional<Plan> planOpt = planRepository.findById(planId);
        if (planOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Plan plan = planOpt.get();
        plan.setStatus(status);
        Plan updatedPlan = planRepository.save(plan);
        return ResponseEntity.ok(updatedPlan);
    }
}


