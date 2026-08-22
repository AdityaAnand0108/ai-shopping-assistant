/**
 * Cross-cutting request handling.
 *
 * <p>{@link com.shopassist.advice.GlobalExceptionHandler} is the single exit
 * for every error in the application. Routing everything through one place is
 * what keeps stack traces, SQL fragments, constraint names and Java type names
 * out of responses.
 */
package com.shopassist.advice;
