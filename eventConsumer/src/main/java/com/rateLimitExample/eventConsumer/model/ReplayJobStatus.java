package com.rateLimitExample.eventConsumer.model;

import java.time.Instant;

public record ReplayJobStatus(
	String replayGroupId,
	String topic,
	String status,
	int processedRecords,
	Integer requestedMaxRecords,
	Instant startedAt,
	Instant finishedAt,
	String lastError
) {
}
