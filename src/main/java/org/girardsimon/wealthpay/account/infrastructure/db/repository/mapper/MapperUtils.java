package org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper;

import tools.jackson.databind.JsonNode;

public final class MapperUtils {

    private MapperUtils() {
    }

    public static JsonNode getRequiredField(JsonNode root, String fieldName) {
        JsonNode jsonNode = root.get(fieldName);
        if (jsonNode == null || jsonNode.isNull()) {
            throw new IllegalStateException("Missing required field '" + fieldName + "'");
        }
        return jsonNode;
    }
}
