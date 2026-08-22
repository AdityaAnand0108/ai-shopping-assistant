/**
 * Sign-in and registration failures.
 *
 * <p>{@code AuthenticationFailedException} deliberately covers an unknown
 * username, a wrong password and a disabled account alike, so the endpoint
 * cannot be used to discover which usernames exist.
 */
package com.shopassist.exception.auth;
