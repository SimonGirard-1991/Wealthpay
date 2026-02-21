package org.girardsimon.wealthpay.account.application.response;

import java.util.Optional;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;

public record CaptureReservationResponse(
    AccountId accountId,
    ReservationId reservationId,
    ReservationCaptureStatus reservationCaptureStatus,
    Optional<Money> money) {}
