package org.girardsimon.wealthpay.customer.application;

import java.util.regex.Pattern;

/**
 * A SHA-256 digest of the client-supplied registration payload, used to tell a genuine retry from a
 * changed payload reusing the same key.
 *
 * <p>It covers client-supplied fields only. Taken over the minted id or the registration instant it
 * would differ on every attempt, turning each legitimate retry into a conflict.
 */
public record Fingerprint(String value) {

  private static final Pattern SHA_256_HEX = Pattern.compile("^[0-9a-f]{64}$");

  public Fingerprint {
    requireSha256Hex(value);
  }

  private static void requireSha256Hex(String value) {
    // IllegalStateException, not IllegalArgumentException: we compute this digest, no client
    // supplies it, and the global handler renders the latter as a 400 blaming the caller.
    if (value == null || !SHA_256_HEX.matcher(value).matches()) {
      throw new IllegalStateException("Fingerprint must be a lowercase 64-character SHA-256 hex");
    }
  }
}
