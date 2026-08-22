/**
 * Authentication plumbing: token issuing, verification, and the filter chain.
 *
 * <p>{@link com.shopassist.security.AppUserPrincipal} is the single point at
 * which a request's identity enters the domain, and
 * {@link com.shopassist.security.CurrentUserService} is the only way to read it.
 * Centralising it means there is no code path — including a tool the model
 * invokes — where identity arrives as untrusted input.
 *
 * <p>Tokens carry the account's random public reference, never its primary key,
 * and the user row is re-read on every request so that disabling an account
 * takes effect immediately.
 */
package com.shopassist.security;
