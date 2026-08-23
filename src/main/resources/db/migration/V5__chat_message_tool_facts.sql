-- Carries the identifiers a turn's tools returned into the next turn.
--
-- Conversation history replays only the text of previous messages, so anything
-- a tool returned - a SKU, an order number - vanished the moment the turn
-- ended. The model was then asked to act on it and had nothing to act with, so
-- it invented one. That is why "yes, place the order" could loop: the SKU and
-- the draft were both real, and both unreachable one turn later.
--
-- Storing them per message rather than per conversation keeps the window
-- honest: history is already limited to the last few turns, and facts age out
-- with the messages that produced them.

ALTER TABLE chat_messages ADD COLUMN tool_facts VARCHAR(500);
