package com.rateLimitExample.eventProducer.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.rateLimitExample.eventProducer.dto.OrderEvent;

@Service
public class OrderProducerService {

	private final KafkaTemplate<String, OrderEvent> kafkaTemplate;
	private final String topicName;

	public OrderProducerService(
		KafkaTemplate<String, OrderEvent> kafkaTemplate,
		@Value("${app.kafka.topic.orders}") String topicName
	) {
		this.kafkaTemplate = kafkaTemplate;
		this.topicName = topicName;
	}

	public OrderEvent send(int index) {
		OrderEvent event = new OrderEvent(
			UUID.randomUUID().toString(),
			"order-" + index,
			Instant.now(),
			"demo-payload-" + index
		);

		kafkaTemplate.send(topicName, event.orderId(), event);
		return event;
	}
}
