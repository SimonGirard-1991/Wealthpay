package org.girardsimon.wealthpay.account.application.response;

import org.girardsimon.wealthpay.account.domain.model.ReservationId;

public record ReserveFundsResponse(
    ReservationId reservationId, ReservationResult reservationResult) {}
