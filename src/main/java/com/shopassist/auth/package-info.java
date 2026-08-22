/**
 * Registration, sign-in and sign-out.
 *
 * <p>Stateless JWT in an {@code Authorization: Bearer} header. Failure
 * responses are deliberately uniform: an unknown username and a wrong password
 * return byte-identical bodies, and a miss still runs one BCrypt comparison so
 * response timing does not separate them either.
 */
package com.shopassist.auth;
