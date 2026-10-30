package com.enersight.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtService jwtService = Mockito.mock(JwtService.class);
    private final UserDetailsServiceImpl userDetailsService = Mockito.mock(UserDetailsServiceImpl.class);
    private final FilterChain chain = Mockito.mock(FilterChain.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest bearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    @Test
    void invalidOrExpiredTokenContinuesUnauthenticated() throws Exception {
        MockHttpServletRequest request = bearer("bad");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.extractUsername("bad")).thenThrow(new JwtException("expired"));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void tokenOfUnknownUserContinuesUnauthenticated() throws Exception {
        MockHttpServletRequest request = bearer("old");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.extractUsername("old")).thenReturn("gone@example.test");
        when(userDetailsService.loadUserByUsername("gone@example.test"))
                .thenThrow(new UsernameNotFoundException("gone"));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }
}
