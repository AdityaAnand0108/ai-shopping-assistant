package com.shopassist.dto.chat;

import java.util.List;

/**
 * What happened behind one reply.
 *
 * <p>Returned alongside every answer so a client can show its provenance rather
 * than asking a shopper to take the assistant's word for it. The brief asks for
 * explainability, and the honest form of that is saying which backend calls
 * produced an answer — and admitting when part of it has no source.
 *
 * @param toolsUsed   backend tools invoked for this turn, in the order called;
 *                    empty for a purely conversational reply
 * @param grounded    false when the reply states an identifier or amount that no
 *                    tool returned
 * @param unsupported the specific values with no source, so a client can mark
 *                    them rather than only warning in general terms
 * @param redacted    true when the reply was replaced because it exposed
 *                    internals
 */
public record TurnInsight(
        List<String> toolsUsed,
        boolean grounded,
        List<String> unsupported,
        boolean redacted
) {
    /** A message refused before it reached the model. */
    public static TurnInsight refused() {
        return new TurnInsight(List.of(), true, List.of(), false);
    }
}
