package org.girardsimon.wealthpay.account.application.response;

public enum ReservationCaptureStatus {
  CAPTURED,
  NO_EFFECT // reservation is absent (never existed OR already handled)
}
