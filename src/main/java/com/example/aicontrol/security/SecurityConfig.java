package com.example.aicontrol.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

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
                )
                // Đối với REST API, khi chưa xác thực trả về 401 Unauthorized thay vì mặc định 403 Forbidden
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                );

        // TODO: Bước sau sẽ thêm JWT và RBAC

        return http.build();
    }
}
