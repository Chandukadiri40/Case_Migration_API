package com.migrationreport.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;
        
        // Anti-clickjacking (permit sameorigin for preview modal)
        httpServletResponse.setHeader("X-Frame-Options", "SAMEORIGIN");
        
        // Prevent MIME sniffing
        httpServletResponse.setHeader("X-Content-Type-Options", "nosniff");
        
        // Content Security Policy
        httpServletResponse.setHeader("Content-Security-Policy", "default-src 'self' http://localhost:8080 http://localhost:5173; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; font-src 'self' data:; img-src 'self' data: blob:; frame-src 'self' http://localhost:8080 http://localhost:5173 blob: data:; frame-ancestors 'self' http://localhost:5173; connect-src 'self' http://localhost:8080 http://localhost:5173;");
        
        // XSS Protection
        httpServletResponse.setHeader("X-XSS-Protection", "1; mode=block");
        
        chain.doFilter(request, response);
    }
}
