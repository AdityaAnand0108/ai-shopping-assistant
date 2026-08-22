/**
 * The registered shopper. One table carries credentials and profile together,
 * so orders and conversations hang off it without an extra join. It is named
 * {@code app_users} because USER is reserved in both MySQL and H2.
 */
package com.shopassist.entity.user;
