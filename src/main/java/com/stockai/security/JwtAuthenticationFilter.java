package com.stockai.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {

            String token = header.substring(7);

            try {
                String username = JwtUtil.getUsername(token);
                String role = JwtUtil.getRole(token);

                // ✅ Spring Security의 hasRole("ADMIN")은 내부적으로 "ROLE_ADMIN"을 찾으므로
                // 접두사 ROLE_ 을 붙여서 권한을 등록해야 함
                var authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + role)
                );

                var authentication = new UsernamePasswordAuthenticationToken(
                        username, null, authorities
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (Exception e) {
                // ✅ 토큰이 유효하지 않거나 만료된 경우: 인증 정보를 세팅하지 않고
                // 그냥 다음 필터로 넘긴다. (막는 건 SecurityConfig의 authorizeHttpRequests가 담당)
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}