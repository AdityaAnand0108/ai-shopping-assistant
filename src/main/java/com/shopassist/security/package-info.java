/**
 * Authentication plumbing: token issuing, verification, revocation, and the
 * filter that turns a bearer header into an authenticated context.
 *
 * <p>{@link com.shopassist.security.AppUserPrincipal} is the single point at
 * which a request's identity enters the domain, and
 * {@link com.shopassist.security.CurrentUserService} is the only way to read
 * it. Centralising it means there is no code path — including a tool the model
 * invokes — where identity arrives as untrusted input.
 */
package com.shopassist.security;
