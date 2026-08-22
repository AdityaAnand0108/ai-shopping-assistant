package com.shopassist.repository.user;

import com.shopassist.entity.user.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

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
