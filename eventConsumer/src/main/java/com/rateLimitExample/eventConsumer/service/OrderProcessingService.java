package com.rateLimitExample.eventConsumer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.rateLimitExample.eventConsumer.dto.OrderEvent;

@Service
public class OrderProcessingService {

	private static final Logger log = LoggerFactory.getLogger(OrderProcessingService.class);

	private final RateLimiter consumerRateLimiter;
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	public OrderProcessingService(RateLimiter consumerRateLimiter) {
		this.consumerRateLimiter = consumerRateLimiter;
	}

	public void process(String rawEvent, String source, int partition, long offset) {
		consumerRateLimiter.acquire();
		OrderEvent event = deserialize(rawEvent);
		log.info(
			"Processing source={} eventId={} orderId={} partition={} offset={}",
			source,
			event.eventId(),
			event.orderId(),
			partition,
			offset
		);

		// Demo business logic.
		try {
			Thread.sleep(250);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Processing interrupted", ex);
		}
	}

	private OrderEvent deserialize(String rawEvent) {
		try {
			return objectMapper.readValue(rawEvent, OrderEvent.class);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalArgumentException("Invalid order event payload", ex);
		}
	}
}
