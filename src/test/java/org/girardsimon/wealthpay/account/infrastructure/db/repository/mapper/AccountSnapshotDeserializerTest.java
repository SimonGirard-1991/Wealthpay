package org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.AccountSnapshot;
import org.girardsimon.wealthpay.account.domain.model.AccountStatus;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record3;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AccountSnapshotDeserializerTest {

  AccountSnapshotDeserializer accountSnapshotDeserializer =
      new AccountSnapshotDeserializer(new ObjectMapper());

  @Test
  void serialize_and_deserialize_should_round_trip_reservations() {
    // Arrange
    UUID accountId = UUID.randomUUID();
    JSONB state =
        JSONB.valueOf(
            """
      {
        "currency": "USD",
        "balance": 100.00,
        "status": "OPENED",
        "reservations": {
          "2dde2dab-fc49-4086-8b27-3ba2762de100": {
            "amount": 40.10,
            "currency": "USD"
          },
          "a463f20d-0086-45e2-b660-be434192bf67": {
            "amount": 12.50,
            "currency": "USD"
          }
        }
      }
      """);
    long version = 100L;
    DSLContext dslContext = DSL.using(SQLDialect.POSTGRES);
    Field<UUID> accountIdField = DSL.field("account_id", UUID.class);
    Field<JSONB> stateField = DSL.field("state", JSONB.class);
    Field<Long> versionField = DSL.field("version", Long.class);
    Record3<UUID, JSONB, Long> record3 =
        dslContext.newRecord(accountIdField, stateField, versionField);
    record3.set(accountIdField, accountId);
    record3.set(stateField, state);
    record3.set(versionField, version);
    // Act
    Optional<AccountSnapshot> snapshotOptional = accountSnapshotDeserializer.apply(record3);

    // Assert
    Map<ReservationId, Money> reservationsMap =
        Map.of(
            ReservationId.of(UUID.fromString("2dde2dab-fc49-4086-8b27-3ba2762de100")),
            Money.of(new BigDecimal("40.10"), SupportedCurrency.USD),
            ReservationId.of(UUID.fromString("a463f20d-0086-45e2-b660-be434192bf67")),
            Money.of(new BigDecimal("12.50"), SupportedCurrency.USD));
    assertThat(snapshotOptional).isPresent();
    AccountSnapshot accountSnapshot = snapshotOptional.get();
    assertAll(
        () -> assertThat(accountSnapshot.accountId()).isEqualTo(AccountId.of(accountId)),
        () -> assertThat(accountSnapshot.currency()).isEqualTo(SupportedCurrency.USD),
        () ->
            assertThat(accountSnapshot.balance())
                .isEqualTo(Money.of(new BigDecimal("100.00"), SupportedCurrency.USD)),
        () -> assertThat(accountSnapshot.status()).isEqualTo(AccountStatus.OPENED),
        () ->
            assertThat(accountSnapshot.reservations())
                .containsExactlyInAnyOrderEntriesOf(reservationsMap));
  }
}
