package com.example.aicontrol.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Tắt CSRF (Cross-Site Request Forgery) vì đây là RESTful API (thường không dùng Session/Cookie)
                .csrf(AbstractHttpConfigurer::disable)

                // Cấu hình phân quyền truy cập cho các endpoint
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/refunds/**").permitAll() // Cho phép truy cập tạm thời không cần xác thực
                        .anyRequest().authenticated() // Các request khác đều yêu cầu xác thực
                );

        // TODO: Bước sau sẽ thêm JWT và RBAC

        return http.build();
    }
}
