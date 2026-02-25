package org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.AccountSnapshot;
import org.girardsimon.wealthpay.account.domain.model.AccountStatus;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.girardsimon.wealthpay.account.jooq.tables.records.AccountSnapshotRecord;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AccountSnapshotSerializerTest {

  AccountSnapshotSerializer serializer = new AccountSnapshotSerializer(new ObjectMapper());

  @Test
  void serialize_should_serialize_snapshot_as_expected() {
    // Arrange
    ObjectMapper objectMapper = new ObjectMapper();
    AccountId accountId = AccountId.of(UUID.fromString("4eadb67c-9d4e-44f7-bf8f-a2e0111e4f35"));
    SupportedCurrency usd = SupportedCurrency.USD;
    Map<ReservationId, Money> reservations =
        Map.of(
            ReservationId.of(UUID.fromString("cf79e4b1-a5dd-4d09-bffe-c18624f2384f")),
            Money.of(new BigDecimal("40.10"), usd),
            ReservationId.of(UUID.fromString("c4c81e66-3608-493a-95d0-7ecf76f2202e")),
            Money.of(new BigDecimal("12.50"), usd));
    AccountSnapshot accountSnapshot =
        new AccountSnapshot(
            accountId,
            usd,
            Money.of(new BigDecimal("100.00"), usd),
            AccountStatus.OPENED,
            reservations,
            3L);

    // Act
    AccountSnapshotRecord row = serializer.apply(accountSnapshot);
    JsonNode actualState = objectMapper.readTree(row.getState().data());

    // Assert
    JsonNode expectedState =
        objectMapper.readTree(
            """
            {
              "currency": "USD",
              "balance": 100.00,
              "status": "OPENED",
              "reservations": {
                "cf79e4b1-a5dd-4d09-bffe-c18624f2384f": {
                  "amount": 40.10,
                  "currency": "USD"
                },
                "c4c81e66-3608-493a-95d0-7ecf76f2202e": {
                  "amount": 12.50,
                  "currency": "USD"
                }
              }
            }
            """);
    assertAll(
        () -> assertThat(row.getAccountId()).isEqualTo(accountId.id()),
        () -> assertThat(row.getVersion()).isEqualTo(3L),
        () -> assertThat(actualState).isEqualTo(expectedState));
  }
}
