/**
 * Registration, sign-in and sign-out.
 *
 * <p>Failure responses are uniform by design, and a miss still runs one BCrypt
 * comparison so response timing does not separate a real account from an
 * unknown one.
 */
package com.shopassist.services.auth;
