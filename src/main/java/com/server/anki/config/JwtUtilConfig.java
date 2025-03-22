package com.server.anki.config;

import com.server.anki.utils.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtUtilConfig {

    @Bean
    public JwtUtil jwtUtil() {
        return new JwtUtil();
    }
}
