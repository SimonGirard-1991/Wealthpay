package org.girardsimon.wealthpay.shared.utils;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import tools.jackson.databind.JsonNode;

public final class MapperUtils {

  private MapperUtils() {}

  public static JsonNode getRequiredField(JsonNode root, String fieldName) {
    JsonNode jsonNode = root.get(fieldName);
    if (jsonNode == null || jsonNode.isNull()) {
      throw new IllegalStateException("Missing required field '" + fieldName + "'");
    }
    return jsonNode;
  }

  public static String headerAsString(Headers headers, String name) {
    Header h = headers.lastHeader(name);
    if (h == null) throw new IllegalArgumentException("Missing header: " + name);
    return new String(h.value(), java.nio.charset.StandardCharsets.UTF_8);
  }
}
