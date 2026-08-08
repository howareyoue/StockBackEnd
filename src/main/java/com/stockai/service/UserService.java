package com.stockai.service;

import com.stockai.dto.LoginRequest;
import com.stockai.dto.SignUpRequest;
import com.stockai.entity.Role;
import com.stockai.entity.User;
import com.stockai.entity.UserStatus;
import com.stockai.repository.UserRepository;
import com.stockai.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public String signup(SignUpRequest dto) {
        if (userRepository.findByUsername(dto.getUsername()).isPresent()) {
            return "이미 존재하는 아이디";
        }

        User user = User.builder()
                .username(dto.getUsername())
                .password(passwordEncoder.encode(dto.getPassword()))
                .name(dto.getName())
                .email(dto.getEmail())
                .role(Role.USER)
                .status(UserStatus.PENDING)
                .build();

        userRepository.save(user);
        return "회원가입 완료 (승인대기)";
    }

    public ResponseEntity<?> login(LoginRequest dto) {
        User user = userRepository.findByUsername(dto.getUsername()).orElse(null);

        if (user == null) {
            return ResponseEntity.badRequest().body("존재하지 않는 아이디");
        }

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            return ResponseEntity.badRequest().body("비밀번호 오류");
        }

        if (user.getStatus() != UserStatus.APPROVED) {
            return ResponseEntity.badRequest().body("관리자 승인 대기중");
        }

        String token = JwtUtil.createToken(user.getUsername(), user.getRole().name());

        Map<String, String> body = new HashMap<>();
        body.put("token", token);
        body.put("role", user.getRole().name());
        body.put("username", user.getUsername());

        return ResponseEntity.ok(body);
    }
}