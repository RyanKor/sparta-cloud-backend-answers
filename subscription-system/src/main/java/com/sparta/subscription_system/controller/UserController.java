package com.sparta.subscription_system.controller;

import com.sparta.subscription_system.entity.User;
import com.sparta.subscription_system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createUser(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String name = request.get("name");
            String password = request.get("password");

            if (email == null || email.trim().isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "이메일은 필수입니다.");
                return ResponseEntity.badRequest().body(error);
            }

            // 이메일 중복 체크
            Optional<User> existingUser = userRepository.findByEmail(email);
            if (existingUser.isPresent()) {
                Map<String, Object> result = new HashMap<>();
                result.put("user", existingUser.get());
                result.put("message", "이미 존재하는 사용자입니다.");
                return ResponseEntity.ok(result);
            }

            // 새 사용자 생성
            User user = new User();
            user.setEmail(email);
            user.setName(name != null ? name : "사용자");
            // 간단한 테스트용 - 실제로는 비밀번호 해싱 필요
            user.setPasswordHash(password != null ? password : "default_password_hash");

            User savedUser = userRepository.save(user);

            Map<String, Object> result = new HashMap<>();
            result.put("user", savedUser);
            result.put("message", "사용자가 생성되었습니다.");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "사용자 생성 중 오류가 발생했습니다: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> getUserById(@PathVariable Long userId) {
        Optional<User> user = userRepository.findById(userId);
        if (user.isPresent()) {
            Map<String, Object> result = new HashMap<>();
            result.put("user", user.get());
            return ResponseEntity.ok(result);
        } else {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "User not found: " + userId);
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/create-or-get")
    public ResponseEntity<Map<String, Object>> createOrGetUser(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            if (email == null || email.trim().isEmpty()) {
                email = "test" + System.currentTimeMillis() + "@example.com";
            }

            // 이메일로 사용자 찾기
            Optional<User> existingUser = userRepository.findByEmail(email);
            if (existingUser.isPresent()) {
                Map<String, Object> result = new HashMap<>();
                result.put("user", existingUser.get());
                result.put("message", "기존 사용자를 찾았습니다.");
                return ResponseEntity.ok(result);
            }

            // 새 사용자 생성
            User user = new User();
            user.setEmail(email);
            user.setName(request.getOrDefault("name", "테스트 사용자"));
            user.setPasswordHash("default_password_hash");

            User savedUser = userRepository.save(user);

            Map<String, Object> result = new HashMap<>();
            result.put("user", savedUser);
            result.put("message", "새 사용자가 생성되었습니다.");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "사용자 생성 중 오류가 발생했습니다: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}


