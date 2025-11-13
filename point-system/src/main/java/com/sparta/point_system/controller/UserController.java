package com.sparta.point_system.controller;

import com.sparta.point_system.entity.User;
import com.sparta.point_system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/user")
    public User createUser(@RequestParam String email, 
                          @RequestParam String passwordHash, 
                          @RequestParam(required = false) String name) {
        User user = new User(email, passwordHash, name);
        return userRepository.save(user);
    }

    @GetMapping("/users")
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @GetMapping("/user/{userId}")
    public Optional<User> getUserById(@PathVariable Long userId) {
        return userRepository.findById(userId);
    }

    @GetMapping("/user/email/{email}")
    public Optional<User> getUserByEmail(@PathVariable String email) {
        return userRepository.findByEmail(email);
    }

    @PutMapping("/user/{userId}")
    public User updateUser(@PathVariable Long userId, 
                          @RequestParam(required = false) String email,
                          @RequestParam(required = false) String passwordHash,
                          @RequestParam(required = false) String name) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (email != null) user.setEmail(email);
            if (passwordHash != null) user.setPasswordHash(passwordHash);
            if (name != null) user.setName(name);
            return userRepository.save(user);
        }
        throw new RuntimeException("User not found with id: " + userId);
    }

    @DeleteMapping("/user/{userId}")
    public String deleteUser(@PathVariable Long userId) {
        userRepository.deleteById(userId);
        return "User deleted successfully";
    }
}

