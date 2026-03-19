package com.rateLimitExample.eventConsumer.model;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public class ReplayJobState {

	private final String replayGroupId;
	private final String topic;
	private final Integer requestedMaxRecords;
	private final Instant startedAt;
	private final AtomicInteger processedRecords = new AtomicInteger();

	private volatile String status = "RUNNING";
	private volatile Instant finishedAt;
	private volatile String lastError;

	public ReplayJobState(String replayGroupId, String topic, Integer requestedMaxRecords) {
		this.replayGroupId = replayGroupId;
		this.topic = topic;
		this.requestedMaxRecords = requestedMaxRecords;
		this.startedAt = Instant.now();
	}

	public void incrementProcessedRecords() {
		processedRecords.incrementAndGet();
	}

	public void markCompleted() {
		status = "COMPLETED";
		finishedAt = Instant.now();
	}

	public void markFailed(Exception ex) {
		status = "FAILED";
		finishedAt = Instant.now();
		lastError = ex.getMessage();
	}

	public ReplayJobStatus snapshot() {
		return new ReplayJobStatus(
			replayGroupId,
			topic,
			status,
			processedRecords.get(),
			requestedMaxRecords,
			startedAt,
			finishedAt,
			lastError
		);
	}
}
