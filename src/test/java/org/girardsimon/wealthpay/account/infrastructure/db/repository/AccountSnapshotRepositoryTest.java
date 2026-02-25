package org.girardsimon.wealthpay.account.infrastructure.db.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.girardsimon.wealthpay.account.jooq.tables.AccountSnapshot.ACCOUNT_SNAPSHOT;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.girardsimon.wealthpay.account.application.AccountSnapshotStore;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.AccountSnapshot;
import org.girardsimon.wealthpay.account.domain.model.AccountStatus;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper.AccountSnapshotDeserializer;
import org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper.AccountSnapshotSerializer;
import org.girardsimon.wealthpay.account.jooq.tables.records.AccountSnapshotRecord;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;

@JooqTest
@Import({
  AccountSnapshotRepository.class,
  AccountSnapshotDeserializer.class,
  AccountSnapshotSerializer.class,
  ObjectMapper.class
})
class AccountSnapshotRepositoryTest extends AbstractContainerTest {

  @Autowired private DSLContext dslContext;
  @Autowired private AccountSnapshotStore accountSnapshotStore;

  @Test
  void load_should_return_empty_when_account_snapshot_does_not_exist() {
    // Arrange
    AccountId accountId = AccountId.newId();

    // Act
    Optional<AccountSnapshot> snapshotLookup = accountSnapshotStore.load(accountId);

    // Assert
    assertThat(snapshotLookup).isEmpty();
  }

  @Test
  void saveSnapshot_should_persist_snapshot_and_support_load() {
    // Arrange
    AccountId accountId = AccountId.of(UUID.fromString("4eadb67c-9d4e-44f7-bf8f-a2e0111e4f35"));
    SupportedCurrency usd = SupportedCurrency.USD;
    Map<ReservationId, Money> reservations =
        Map.of(
            ReservationId.of(UUID.fromString("cf79e4b1-a5dd-4d09-bffe-c18624f2384f")),
            Money.of(new BigDecimal("40.10"), usd),
            ReservationId.of(UUID.fromString("c4c81e66-3608-493a-95d0-7ecf76f2202e")),
            Money.of(new BigDecimal("12.50"), usd));
    AccountSnapshot snapshotToSave =
        new AccountSnapshot(
            accountId,
            usd,
            Money.of(new BigDecimal("100.00"), usd),
            AccountStatus.OPENED,
            reservations,
            3L);

    // Act
    accountSnapshotStore.saveSnapshot(snapshotToSave);

    // Assert
    AccountSnapshotRecord row =
        dslContext
            .selectFrom(ACCOUNT_SNAPSHOT)
            .where(ACCOUNT_SNAPSHOT.ACCOUNT_ID.eq(accountId.id()))
            .fetchOne();
    Optional<AccountSnapshot> loadedSnapshot = accountSnapshotStore.load(accountId);
    assertAll(
        () -> assertThat(row).isNotNull(),
        () -> assertThat(row.getVersion()).isEqualTo(3L),
        () ->
            assertThat(row.getSchemaVersion()).isEqualTo(AccountSnapshotSerializer.SCHEMA_VERSION),
        () -> assertThat(loadedSnapshot).contains(snapshotToSave));
  }

  @Test
  void saveSnapshot_should_update_existing_snapshot_when_incoming_version_is_higher() {
    // Arrange
    AccountId accountId = AccountId.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    AccountSnapshot initialSnapshot =
        new AccountSnapshot(
            accountId,
            usd,
            Money.of(new BigDecimal("100.00"), usd),
            AccountStatus.OPENED,
            Map.of(),
            2L);
    Map<ReservationId, Money> updatedReservations =
        Map.of(
            ReservationId.of(UUID.fromString("ec0fef9d-44f2-4e0b-b111-e8996e986501")),
            Money.of(new BigDecimal("25.00"), usd));
    AccountSnapshot updatedSnapshot =
        new AccountSnapshot(
            accountId,
            usd,
            Money.of(new BigDecimal("150.00"), usd),
            AccountStatus.CLOSED,
            updatedReservations,
            5L);
    accountSnapshotStore.saveSnapshot(initialSnapshot);

    // Act
    accountSnapshotStore.saveSnapshot(updatedSnapshot);

    // Assert
    AccountSnapshotRecord row =
        dslContext
            .selectFrom(ACCOUNT_SNAPSHOT)
            .where(ACCOUNT_SNAPSHOT.ACCOUNT_ID.eq(accountId.id()))
            .fetchOne();
    Optional<AccountSnapshot> loadedSnapshot = accountSnapshotStore.load(accountId);
    assertAll(
        () -> assertThat(row).isNotNull(),
        () -> assertThat(row.getVersion()).isEqualTo(5L),
        () -> assertThat(loadedSnapshot).contains(updatedSnapshot));
  }

  @Test
  void saveSnapshot_should_ignore_write_when_version_is_not_newer() {
    // Arrange
    AccountId accountId = AccountId.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    AccountSnapshot currentSnapshot =
        new AccountSnapshot(
            accountId,
            usd,
            Money.of(new BigDecimal("100.00"), usd),
            AccountStatus.OPENED,
            Map.of(),
            5L);
    Map<ReservationId, Money> reservations =
        Map.of(
            ReservationId.of(UUID.fromString("8940ad07-062f-4f04-b8be-409da07d14f8")),
            Money.of(new BigDecimal("20.00"), usd));
    AccountSnapshot sameVersionSnapshot =
        new AccountSnapshot(
            accountId,
            usd,
            Money.of(new BigDecimal("999.99"), usd),
            AccountStatus.CLOSED,
            reservations,
            5L);
    AccountSnapshot olderSnapshot =
        new AccountSnapshot(
            accountId,
            usd,
            Money.of(new BigDecimal("1.00"), usd),
            AccountStatus.CLOSED,
            Map.of(),
            4L);
    accountSnapshotStore.saveSnapshot(currentSnapshot);

    // Act
    accountSnapshotStore.saveSnapshot(sameVersionSnapshot);
    accountSnapshotStore.saveSnapshot(olderSnapshot);

    // Assert
    AccountSnapshotRecord row =
        dslContext
            .selectFrom(ACCOUNT_SNAPSHOT)
            .where(ACCOUNT_SNAPSHOT.ACCOUNT_ID.eq(accountId.id()))
            .fetchOne();
    int numberOfRows =
        dslContext.fetchCount(ACCOUNT_SNAPSHOT, ACCOUNT_SNAPSHOT.ACCOUNT_ID.eq(accountId.id()));
    Optional<AccountSnapshot> loadedSnapshot = accountSnapshotStore.load(accountId);
    assertAll(
        () -> assertThat(numberOfRows).isEqualTo(1),
        () -> assertThat(row).isNotNull(),
        () -> assertThat(row.getVersion()).isEqualTo(5L),
        () -> assertThat(loadedSnapshot).contains(currentSnapshot));
  }
}
