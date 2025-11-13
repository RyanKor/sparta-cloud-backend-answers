package com.sparta.point_system.util;

import com.sparta.point_system.entity.User;
import com.sparta.point_system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtil {

    @Autowired
    private UserRepository userRepository;

    public String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            return userDetails.getUsername();
        }
        return null;
    }

    public Long getCurrentUserId() {
        String email = getCurrentUserEmail();
        if (email != null) {
            return userRepository.findByEmail(email)
                    .map(User::getUserId)
                    .orElse(null);
        }
        return null;
    }

    public User getCurrentUser() {
        String email = getCurrentUserEmail();
        if (email != null) {
            return userRepository.findByEmail(email).orElse(null);
        }
        return null;
    }
}

