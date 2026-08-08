package com.stockai.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Date;

public class JwtUtil {

    // ✅ 하드코딩 대신 환경변수 JWT_SECRET에서 읽어온다.
    // 로컬 개발 편의를 위해 값이 없으면 개발용 기본값을 쓰되,
    // 운영 배포 시에는 반드시 환경변수로 별도의 강력한 값을 설정해야 한다.
    private static final String SECRET = resolveSecret();

    private static final SecretKey KEY =
            Keys.hmacShaKeyFor(SECRET.getBytes());

    private static String resolveSecret() {
        String envSecret = System.getenv("JWT_SECRET");

        if (envSecret != null && !envSecret.isBlank()) {
            return envSecret;
        }

        // ⚠️ 로컬 개발 전용 기본값. 운영 배포 시 절대 이 값이 쓰이면 안 됨.
        return "dev_only_local_secret_do_not_use_in_production";
    }

    public static String createToken(String username, String role){

        return Jwts.builder()
                .setSubject(username)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(
                        new Date(System.currentTimeMillis()
                                + 1000L * 60 * 60 * 24))
                .signWith(KEY)
                .compact();
    }

    public static String getUsername(String token){

        return Jwts.parserBuilder()
                .setSigningKey(KEY)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public static String getRole(String token){

        return Jwts.parserBuilder()
                .setSigningKey(KEY)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("role", String.class);
    }
}