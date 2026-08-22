/**
 * The registered shopper.
 *
 * <p>One table carries credentials and profile together, so orders and
 * conversations hang off it without an extra join. It is named
 * {@code app_users} because USER is reserved in both MySQL and H2.
 *
 * <p>The surrogate key never leaves the server; anything exposed over HTTP uses
 * the random public reference instead.
 */
package com.shopassist.user;
