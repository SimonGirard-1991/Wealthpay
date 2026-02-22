package org.girardsimon.wealthpay.account.domain.model;

import org.girardsimon.wealthpay.account.domain.exception.ReservationAlreadyCanceledException;
import org.girardsimon.wealthpay.account.domain.exception.ReservationAlreadyCapturedException;

public enum ReservationPhase {
  CAPTURED {
    @Override
    public void ensureCanCapture() {
      // Idempotent
    }

    @Override
    public void ensureCanCancel() {
      throw new ReservationAlreadyCapturedException("Cannot cancel a captured reservation");
    }
  },
  CANCELED {
    @Override
    public void ensureCanCapture() {
      throw new ReservationAlreadyCanceledException("Cannot capture a canceled reservation");
    }

    @Override
    public void ensureCanCancel() {
      // Idempotent
    }
  },
  RESERVED {
    @Override
    public void ensureCanCapture() {
      // Allowed
    }

    @Override
    public void ensureCanCancel() {
      // Allowed
    }
  };

  public abstract void ensureCanCapture();

  public abstract void ensureCanCancel();
}
