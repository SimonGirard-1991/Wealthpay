package org.girardsimon.wealthpay.account.application.response;

import java.util.Optional;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;

public record CancelReservationResponse(
    AccountId accountId,
    ReservationId reservationId,
    Optional<Money> money,
    CancelReservationStatus cancelReservationStatus) {}
