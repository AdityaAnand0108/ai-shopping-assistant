/**
 * Cross-cutting pieces: shared exceptions, the error handler, paging, seeding
 * and configuration records.
 *
 * <p>{@link com.shopassist.common.GlobalExceptionHandler} is the single exit
 * for every error. Routing everything through it is what keeps stack traces,
 * SQL fragments, constraint names and Java type names out of responses.
 */
package com.shopassist.common;
