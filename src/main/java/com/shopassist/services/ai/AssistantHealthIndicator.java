package com.shopassist.services.ai;

import com.shopassist.config.ai.ModelProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports whether the model server is answering.
 *
 * <p>Surfaced at {@code /actuator/health} so an operator can tell a model outage
 * apart from an application fault without reading logs. The detail is
 * deliberately thin — which model is configured, and whether it responds — since
 * the health endpoint is reachable without a token.
 */
@Component("assistantModel")
public class AssistantHealthIndicator implements HealthIndicator {

    private final AssistantModel assistantModel;
    private final ModelProperties properties;

    public AssistantHealthIndicator(AssistantModel assistantModel, ModelProperties properties) {
        this.assistantModel = assistantModel;
        this.properties = properties;
    }

    @Override
    public Health health() {
        return assistantModel.isAvailable()
                ? Health.up().withDetail("model", properties.chatModel()).build()
                : Health.down().withDetail("model", properties.chatModel()).build();
    }
}
