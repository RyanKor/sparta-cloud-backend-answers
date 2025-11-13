package com.sparta.point_system.controller;

import com.sparta.point_system.entity.MembershipLevel;
import com.sparta.point_system.repository.MembershipLevelRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class MembershipLevelController {

    @Autowired
    private MembershipLevelRepository membershipLevelRepository;

    @PostMapping("/membership-level")
    public MembershipLevel createMembershipLevel(@RequestParam String name,
                                                 @RequestParam BigDecimal pointAccrualRate,
                                                 @RequestParam(required = false) String benefitsDescription) {
        MembershipLevel level = new MembershipLevel(name, pointAccrualRate, benefitsDescription);
        return membershipLevelRepository.save(level);
    }

    @GetMapping("/membership-levels")
    public List<MembershipLevel> getAllMembershipLevels() {
        return membershipLevelRepository.findAll();
    }

    @GetMapping("/membership-level/{levelId}")
    public Optional<MembershipLevel> getMembershipLevelById(@PathVariable Long levelId) {
        return membershipLevelRepository.findById(levelId);
    }

    @GetMapping("/membership-level/name/{name}")
    public Optional<MembershipLevel> getMembershipLevelByName(@PathVariable String name) {
        return membershipLevelRepository.findByName(name);
    }

    @PutMapping("/membership-level/{levelId}")
    public MembershipLevel updateMembershipLevel(@PathVariable Long levelId,
                                                 @RequestParam(required = false) String name,
                                                 @RequestParam(required = false) BigDecimal pointAccrualRate,
                                                 @RequestParam(required = false) String benefitsDescription) {
        Optional<MembershipLevel> levelOpt = membershipLevelRepository.findById(levelId);
        if (levelOpt.isPresent()) {
            MembershipLevel level = levelOpt.get();
            if (name != null) level.setName(name);
            if (pointAccrualRate != null) level.setPointAccrualRate(pointAccrualRate);
            if (benefitsDescription != null) level.setBenefitsDescription(benefitsDescription);
            return membershipLevelRepository.save(level);
        }
        throw new RuntimeException("MembershipLevel not found with id: " + levelId);
    }

    @DeleteMapping("/membership-level/{levelId}")
    public String deleteMembershipLevel(@PathVariable Long levelId) {
        membershipLevelRepository.deleteById(levelId);
        return "MembershipLevel deleted successfully";
    }
}

