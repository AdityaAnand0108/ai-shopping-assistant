package com.shopassist.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Registered shoppers.
 *
 * <p>Lookups are by username for sign-in and by public reference for token
 * verification. Nothing resolves a user from a value a client supplies as an
 * identifier, so account enumeration has no entry point here.
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByPublicRef(String publicRef);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);
}
