/**
 * Conversations and turns, always scoped to their owner, and always read in
 * bounded windows so a long thread cannot overflow the model's context.
 */
package com.shopassist.repository.chat;
