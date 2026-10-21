package com.alexrifas.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Adds a correlation id and records the HTTP outcome without logging secrets or query strings. */
@Component
public class RastreoFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RastreoFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String anterior = MDC.get("requestId");
        String requestId = UUID.randomUUID().toString();
        long inicio = System.nanoTime();
        MDC.put("requestId", requestId);
        response.setHeader("X-Request-Id", requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long ms = (System.nanoTime() - inicio) / 1_000_000L;
            log.info("{} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(), response.getStatus(), ms);
            if (anterior == null) MDC.remove("requestId"); else MDC.put("requestId", anterior);
        }
    }
}
