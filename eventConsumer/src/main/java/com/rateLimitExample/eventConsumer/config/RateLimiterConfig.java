package com.rateLimitExample.eventConsumer.config;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimiterConfig {

	@Bean
	RateLimiter consumerRateLimiter(@Value("${app.processing.rate-per-second}") double ratePerSecond) {
		return RateLimiter.create(ratePerSecond);
	}
}
