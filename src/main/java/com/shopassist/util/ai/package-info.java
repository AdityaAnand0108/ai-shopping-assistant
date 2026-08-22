/**
 * The assistant's standing instructions, kept in code and version-controlled
 * alongside the behaviour they govern.
 *
 * <p>Everything here is a request to the model, not a constraint on it. The
 * prompt reduces bad answers; it does not prevent them. The guarantees that
 * actually hold live in {@code services.ai.tools} and the service layer.
 */
package com.shopassist.util.ai;
