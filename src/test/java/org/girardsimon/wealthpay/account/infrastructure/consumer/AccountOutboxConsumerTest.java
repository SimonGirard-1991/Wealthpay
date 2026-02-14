package org.girardsimon.wealthpay.account.infrastructure.consumer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.girardsimon.wealthpay.account.application.AccountBalanceProjector;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.EventId;
import org.girardsimon.wealthpay.account.infrastructure.consumer.mapper.AccountEventDeserializer;
import org.girardsimon.wealthpay.shared.config.KafkaErrorConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    classes = {AccountOutboxConsumer.class, AccountEventDeserializer.class, KafkaErrorConfig.class})
@ImportAutoConfiguration({KafkaAutoConfiguration.class, JacksonAutoConfiguration.class})
@EmbeddedKafka(
    partitions = 1,
    topics = "wealthpay.AccountEvent",
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestPropertySource(
    properties = {
      "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
      "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
      "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
      "spring.kafka.properties.schema.registry.url=mock://unused"
    })
class AccountOutboxConsumerTest {

  public static final String ACCOUNT_EVENT_TOPIC = "wealthpay.AccountEvent";

  @MockitoBean AccountBalanceProjector accountBalanceProjector;
  @Autowired KafkaTemplate<String, String> kafkaTemplate;

  @Test
  void should_deserialize_and_project_account_opened_event() throws Exception {
    // Arrange
    AccountId accountId = AccountId.newId();
    EventId eventId = EventId.newId();
    Instant now = Instant.now();
    String payload =
        """
      {"currency":"USD","initialBalance":100.00,"occurredAt":"%s"}
      """
            .formatted(now);
    RecordHeaders headers = new RecordHeaders();
    headers.add("id", eventId.id().toString().getBytes(UTF_8));
    headers.add("eventType", "AccountOpened".getBytes(UTF_8));
    headers.add("occurredAt", now.toString().getBytes(UTF_8));
    headers.add("aggregateVersion", "1".getBytes(UTF_8));
    ProducerRecord<String, String> producerRecord =
        new ProducerRecord<>(ACCOUNT_EVENT_TOPIC, 0, accountId.id().toString(), payload, headers);

    // Act
    kafkaTemplate.send(producerRecord).get(5, TimeUnit.SECONDS);

    // Assert
    verify(accountBalanceProjector, timeout(5000))
        .project(
            argThat(
                events ->
                    events.size() == 1
                        && events.getFirst() instanceof AccountOpened opened
                        && opened.accountId().equals(accountId)
                        && opened.eventId().equals(eventId)));
  }
}
