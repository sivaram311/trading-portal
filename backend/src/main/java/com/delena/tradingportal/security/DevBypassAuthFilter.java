package com.delena.tradingportal.security;

import com.delena.tradingportal.config.TradingProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * DEV-ONLY authentication shim. Active only when {@code trading.security.dev-bypass=true}. Accepts
 * a fixed test token via {@code X-Dev-Token} header or {@code Authorization: Bearer <token>} so
 * engines/APIs are testable locally when CSS JWKS is unreachable. Never enabled by default; must
 * never ship to PREPROD/PROD.
 */
public class DevBypassAuthFilter extends OncePerRequestFilter {

    private final String expectedToken;

    public DevBypassAuthFilter(TradingProperties props) {
        this.expectedToken = props.getSecurity().getDevToken();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = request.getHeader("X-Dev-Token");
        if (token == null) {
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                token = auth.substring("Bearer ".length()).trim();
            }
        }
        if (expectedToken.equals(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            var authentication = new UsernamePasswordAuthenticationToken(
                    "dev-operator", null, List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        chain.doFilter(request, response);
    }
}
