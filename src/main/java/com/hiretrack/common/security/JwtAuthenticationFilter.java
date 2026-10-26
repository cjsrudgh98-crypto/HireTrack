package com.hiretrack.common.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authorization: Bearer {token} 헤더를 읽어 principal 에 사용자 ID(Long)를,
 * 권한에 토큰 범위(SCOPE_APP / SCOPE_EXTENSION)를 넣는다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            jwtProvider.parse(header.substring(BEARER_PREFIX.length()))
                    .ifPresent(p -> SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(p.userId(), null,
                                    List.of(new SimpleGrantedAuthority(p.scope().authority())))));
        }
        chain.doFilter(request, response);
    }
}
