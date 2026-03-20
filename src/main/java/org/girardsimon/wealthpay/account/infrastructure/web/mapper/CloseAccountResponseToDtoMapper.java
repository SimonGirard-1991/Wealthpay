package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import java.util.function.Function;
import org.girardsimon.wealthpay.account.api.generated.model.CloseAccountResponseDto;
import org.girardsimon.wealthpay.account.api.generated.model.TransactionStatusDto;
import org.girardsimon.wealthpay.account.application.response.TransactionStatus;
import org.springframework.stereotype.Component;

@Component
public class CloseAccountResponseToDtoMapper
    implements Function<TransactionStatus, CloseAccountResponseDto> {

  @Override
  public CloseAccountResponseDto apply(TransactionStatus transactionStatus) {
    return new CloseAccountResponseDto()
        .status(TransactionStatusDto.valueOf(transactionStatus.name()));
  }
}
