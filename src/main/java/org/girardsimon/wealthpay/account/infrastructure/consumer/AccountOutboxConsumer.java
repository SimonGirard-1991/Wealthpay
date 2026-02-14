package org.girardsimon.wealthpay.account.infrastructure.consumer;

import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.girardsimon.wealthpay.account.application.AccountBalanceProjector;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.exception.UnsupportedCurrencyException;
import org.girardsimon.wealthpay.account.infrastructure.consumer.mapper.AccountEventDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AccountOutboxConsumer {

  private static final Logger log = LoggerFactory.getLogger(AccountOutboxConsumer.class);

  private final AccountBalanceProjector accountBalanceProjector;

  private final AccountEventDeserializer accountEventDeserializer;

  public AccountOutboxConsumer(
      AccountBalanceProjector accountBalanceProjector,
      AccountEventDeserializer accountEventDeserializer) {
    this.accountBalanceProjector = accountBalanceProjector;
    this.accountEventDeserializer = accountEventDeserializer;
  }

  @KafkaListener(
      topics = "${kafka.topic.account.outbox}",
      groupId = "${spring.kafka.consumer.group-id}")
  public void consumeAccountEventOutbox(ConsumerRecord<String, Object> consumerRecord) {
    try {
      log.info(
          "Consuming account event topic={} partition={} offset={} key={}",
          consumerRecord.topic(),
          consumerRecord.partition(),
          consumerRecord.offset(),
          consumerRecord.key());
      AccountEvent event = accountEventDeserializer.apply(consumerRecord);
      accountBalanceProjector.project(List.of(event));
    } catch (UnsupportedCurrencyException e) {
      log.warn("Ignoring unsupported event {}", consumerRecord.value());
      throw new IllegalArgumentException(e);
    }
  }
}
