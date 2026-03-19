package com.rateLimitExample.eventConsumer.dto;

import java.time.Instant;

public record OrderEvent(
	String eventId,
	String orderId,
	Instant createdAt,
	String payload
) {
}
