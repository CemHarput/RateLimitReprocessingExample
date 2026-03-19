package com.rateLimitExample.eventConsumer.service;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.rateLimitExample.eventConsumer.model.ReplayJobState;
import com.rateLimitExample.eventConsumer.model.ReplayJobStatus;

@Service
public class ReplayService {

	private final OrderProcessingService orderProcessingService;
	private final String topicName;
	private final String bootstrapServers;
	private final ExecutorService replayExecutor = Executors.newCachedThreadPool();
	private final Map<String, ReplayJobState> jobs = new ConcurrentHashMap<>();

	public ReplayService(
		OrderProcessingService orderProcessingService,
		@Value("${app.kafka.topic.orders}") String topicName,
		@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers
	) {
		this.orderProcessingService = orderProcessingService;
		this.topicName = topicName;
		this.bootstrapServers = bootstrapServers;
	}

	public ReplayJobStatus replayFromBeginning(Integer maxRecords) {
		String replayGroupId = "orders-processor-replay-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
			.withZone(java.time.ZoneOffset.UTC)
			.format(Instant.now());

		ReplayJobState jobState = new ReplayJobState(replayGroupId, topicName, maxRecords);
		jobs.put(replayGroupId, jobState);

		replayExecutor.submit(() -> runReplay(jobState, maxRecords));
		return jobState.snapshot();
	}

	public List<ReplayJobStatus> listJobs() {
		return jobs.values().stream()
			.map(ReplayJobState::snapshot)
			.toList();
	}

	private void runReplay(ReplayJobState jobState, Integer maxRecords) {
		try (KafkaConsumer<String, String> consumer = createReplayConsumer(jobState.snapshot().replayGroupId())) {
			consumer.subscribe(List.of(topicName));

			while (consumer.assignment().isEmpty()) {
				consumer.poll(Duration.ofMillis(200));
			}

			consumer.seekToBeginning(consumer.assignment());

			int idlePolls = 0;
			while (idlePolls < 3) {
				ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
				if (records.isEmpty()) {
					idlePolls++;
					continue;
				}

				idlePolls = 0;
				for (ConsumerRecord<String, String> record : records) {
					orderProcessingService.process(
						record.value(),
						jobState.snapshot().replayGroupId(),
						record.partition(),
						record.offset()
					);
					jobState.incrementProcessedRecords();

					if (maxRecords != null && jobState.snapshot().processedRecords() >= maxRecords) {
						jobState.markCompleted();
						return;
					}
				}
			}

			jobState.markCompleted();
		}
		catch (Exception ex) {
			jobState.markFailed(ex);
		}
	}

	private KafkaConsumer<String, String> createReplayConsumer(String replayGroupId) {
		Map<String, Object> properties = new HashMap<>();
		properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		properties.put(ConsumerConfig.GROUP_ID_CONFIG, replayGroupId);
		properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
		properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

		return new KafkaConsumer<>(properties, new StringDeserializer(), new StringDeserializer());
	}

	@PreDestroy
	void shutdown() {
		replayExecutor.shutdownNow();
	}
}
