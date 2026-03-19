package com.rateLimitExample.eventConsumer.listener;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.rateLimitExample.eventConsumer.service.OrderProcessingService;

@Component
public class OrderListener {

	private final OrderProcessingService orderProcessingService;

	public OrderListener(OrderProcessingService orderProcessingService) {
		this.orderProcessingService = orderProcessingService;
	}

	@KafkaListener(topics = "${app.kafka.topic.orders}")
	public void onMessage(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
		orderProcessingService.process(
			record.value(),
			"live-consumer",
			record.partition(),
			record.offset()
		);
		acknowledgment.acknowledge();
	}
}
