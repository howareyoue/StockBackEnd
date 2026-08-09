package com.stockai.service;

import com.stockai.dto.LoginRequest;
import com.stockai.dto.SignUpRequest;
import com.stockai.entity.User;
import com.stockai.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void loginShouldReturnMessageWhenUserDoesNotExist() {
        LoginRequest request = new LoginRequest();
        request.setUsername("unknown");
        request.setPassword("password");

        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        ResponseEntity<?> result = userService.login(request);

        assertEquals("존재하지 않는 아이디", result.getBody());
        verify(userRepository).findByUsername("unknown");
    }

    @Test
    void signupShouldCreatePendingUser() {
        SignUpRequest request = new SignUpRequest();
        request.setUsername("newuser");
        request.setPassword("password");
        request.setName("새유저");
        request.setEmail("new@example.com");

        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String result = userService.signup(request);

        assertEquals("회원가입 완료 (승인대기)", result);
        verify(userRepository).save(any(User.class));
    }
}