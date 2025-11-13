package com.sparta.subscription_system.repository;

import com.sparta.subscription_system.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlanRepository extends JpaRepository<Plan, Long> {
    List<Plan> findByStatus(Plan.PlanStatus status);
}


