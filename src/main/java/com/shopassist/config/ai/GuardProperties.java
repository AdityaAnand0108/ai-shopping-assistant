package com.shopassist.config.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Guardrail settings.
 *
 * <p>Under {@code shopassist.guardrails} rather than {@code shopassist.guard},
 * which {@code ChatProperties} already owns. Two records binding one prefix is
 * legal but reads as a mistake, and it makes it ambiguous which record a new key
 * belongs to.
 *
 * @param blockInjection      refuse messages that look like instruction-override
 *                            attempts, without spending a model call on them
 * @param scanOutput          check replies for leaked internals before returning
 * @param checkGrounding      flag replies quoting figures no tool returned
 * @param messagesPerMinute   chat turns allowed per shopper per minute
 */
@ConfigurationProperties(prefix = "shopassist.guardrails")
public record GuardProperties(
        boolean blockInjection,
        boolean scanOutput,
        boolean checkGrounding,
        int messagesPerMinute
) {
    public GuardProperties {
        if (messagesPerMinute <= 0) {
            messagesPerMinute = 20;
        }
    }
}
