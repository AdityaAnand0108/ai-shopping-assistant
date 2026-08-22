package com.shopassist.enums.user;

/**
 * What an account is allowed to do.
 *
 * <p>Every seeded and self-registered account is a {@link #CUSTOMER}.
 * {@link #ADMIN} exists so the column and the security expression have a
 * meaningful domain, but nothing grants it and no endpoint requires it yet.
 */
public enum UserRole {
    CUSTOMER,
    ADMIN
}
