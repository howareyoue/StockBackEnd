package com.stockai.controller;

import com.stockai.entity.User;
import com.stockai.entity.UserStatus;
import com.stockai.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class AdminController {

    private final UserRepository userRepository;

    @GetMapping("/pending")
    public List<User> pending(){

        return userRepository.findByStatus(
                UserStatus.PENDING);
    }

    @PutMapping("/approve/{id}")
    public String approve(
            @PathVariable Long id){

        User user =
                userRepository.findById(id).orElseThrow();

        user.setStatus(UserStatus.APPROVED);

        userRepository.save(user);

        return "승인 완료";
    }

    @PutMapping("/reject/{id}")
    public String reject(
            @PathVariable Long id){

        User user =
                userRepository.findById(id).orElseThrow();

        user.setStatus(UserStatus.REJECTED);

        userRepository.save(user);

        return "거절 완료";
    }
}