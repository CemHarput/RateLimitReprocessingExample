package com.rateLimitExample.eventProducer.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rateLimitExample.eventProducer.dto.OrderEvent;
import com.rateLimitExample.eventProducer.service.OrderProducerService;

@RestController
@RequestMapping("/orders")
public class OrderController {

	private final OrderProducerService orderProducerService;

	public OrderController(OrderProducerService orderProducerService) {
		this.orderProducerService = orderProducerService;
	}

	@PostMapping("/produce/{count}")
	public List<OrderEvent> produce(@PathVariable int count) {
		List<OrderEvent> events = new ArrayList<>(count);
		for (int i = 1; i <= count; i++) {
			events.add(orderProducerService.send(i));
		}
		return events;
	}
}
