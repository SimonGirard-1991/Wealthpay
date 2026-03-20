package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.girardsimon.wealthpay.account.api.generated.model.TransactionStatusDto;
import org.girardsimon.wealthpay.account.application.response.TransactionStatus;
import org.junit.jupiter.api.Test;

class CloseAccountResponseToDtoMapperTest {

  CloseAccountResponseToDtoMapper mapper = new CloseAccountResponseToDtoMapper();

  @Test
  void map_transaction_status_to_close_account_response_dto() {
    // Act
    TransactionStatusDto committedStatus = mapper.apply(TransactionStatus.COMMITTED).getStatus();
    TransactionStatusDto noEffectStatus = mapper.apply(TransactionStatus.NO_EFFECT).getStatus();

    // Assert
    assertAll(
        () -> assertThat(committedStatus).isEqualTo(TransactionStatusDto.COMMITTED),
        () -> assertThat(noEffectStatus).isEqualTo(TransactionStatusDto.NO_EFFECT));
  }
}
