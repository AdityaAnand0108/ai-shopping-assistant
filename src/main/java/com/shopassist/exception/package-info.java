/**
 * Application exceptions, translated to RFC 7807 problem documents by
 * {@link com.shopassist.advice.GlobalExceptionHandler}.
 *
 * <p>Types here are chosen for what a caller is allowed to learn, not for what
 * went wrong internally. {@code ResourceNotFoundException} covers both "no such
 * thing" and "not yours" on purpose, so the two cannot be told apart.
 */
package com.shopassist.exception;
