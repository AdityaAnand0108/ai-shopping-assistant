/**
 * Running a chat turn.
 *
 * <p>Note the split between the two classes here: the database work happens in
 * two short transactions with the model call in neither. One transaction around
 * the whole turn would pin a pooled connection for seconds, and an ordinary
 * not-found from a tool would mark it rollback-only and fail the request at
 * commit.
 */
package com.shopassist.services.chat;
