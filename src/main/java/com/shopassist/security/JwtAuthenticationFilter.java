package com.shopassist.security;

import com.shopassist.entity.user.AppUser;
import com.shopassist.repository.user.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Turns a {@code Authorization: Bearer <token>} header into an authenticated
 * security context.
 *
 * <p>A bad, expired or revoked token is not an error here — the filter simply
 * leaves the context empty and lets the authorization rules decide. That keeps
 * public endpoints reachable with a stale token still sitting in the client.
 *
 * <p>The user row is re-read on every request rather than trusted from the
 * token body, so disabling an account takes effect immediately instead of
 * waiting out the token's remaining lifetime.
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final TokenDenylist denylist;
    private final AppUserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   TokenDenylist denylist,
                                   AppUserRepository userRepository) {
        this.jwtService = jwtService;
        this.denylist = denylist;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            extractToken(request)
                    .flatMap(jwtService::verify)
                    .filter(verified -> !denylist.isRevoked(verified.tokenId()))
                    .flatMap(this::loadActiveUser)
                    .ifPresent(authentication -> {
                        authentication.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        }

        filterChain.doFilter(request, response);
    }

    private Optional<UsernamePasswordAuthenticationToken> loadActiveUser(
            JwtService.VerifiedToken verified) {

        Optional<AppUser> user = userRepository.findByPublicRef(verified.publicRef())
                .filter(AppUser::isEnabled);

        if (user.isEmpty()) {
            log.debug("Token carried a valid signature but no active account");
            return Optional.empty();
        }

        AppUserPrincipal principal = AppUserPrincipal.of(
                user.get(), verified.tokenId(), verified.expiresAt());
        return Optional.of(new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority(principal.authority()))));
    }

    private static Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String token = header.substring(PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }
}
