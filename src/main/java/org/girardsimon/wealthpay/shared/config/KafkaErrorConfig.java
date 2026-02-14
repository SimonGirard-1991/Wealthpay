package org.girardsimon.wealthpay.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaErrorConfig {

  @Value("${kafka.consumer.retry.initial-interval-ms}")
  private long initialIntervalMs;

  @Value("${kafka.consumer.retry.multiplier}")
  private double multiplier;

  @Bean
  public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
    var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
    var backOff = new ExponentialBackOff(initialIntervalMs, multiplier);
    DefaultErrorHandler defaultErrorHandler = new DefaultErrorHandler(recoverer, backOff);
    defaultErrorHandler.addNotRetryableExceptions(
        IllegalStateException.class, IllegalArgumentException.class);
    return defaultErrorHandler;
  }
}
