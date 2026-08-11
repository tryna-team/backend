package com.tryna.domain.alarm.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tryna.global.exception.AlarmErrorCode;
import com.tryna.global.exception.BusinessException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;

public final class AlarmCursorCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter UPDATED_AT_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private AlarmCursorCodec() {
    }

    public record Cursor(LocalDateTime updatedAt, Long reminderId) {
    }

    public static String encode(Cursor cursor) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(
                    new CursorPayload(cursor.updatedAt().format(UPDATED_AT_FORMATTER), cursor.reminderId())
            );
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
        } catch (Exception e) {
            throw new IllegalStateException("알람 커서 인코딩에 실패했습니다.", e);
        }
    }

    public static Cursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            throw new BusinessException(AlarmErrorCode.F100_ALARMLIST_400_2);
        }

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            JsonNode node = OBJECT_MAPPER.readTree(decoded);

            String updatedAtText = node.path("updatedAt").asText(null);
            JsonNode reminderIdNode = node.get("reminderId");

            if (updatedAtText == null || reminderIdNode == null || reminderIdNode.isNull()) {
                throw new BusinessException(AlarmErrorCode.F100_ALARMLIST_400_2);
            }

            LocalDateTime updatedAt = LocalDateTime.parse(updatedAtText, UPDATED_AT_FORMATTER);
            Long reminderId = reminderIdNode.asLong();

            if (reminderId <= 0) {
                throw new BusinessException(AlarmErrorCode.F100_ALARMLIST_400_2);
            }

            return new Cursor(updatedAt, reminderId);
        } catch (BusinessException e) {
            throw e;
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new BusinessException(AlarmErrorCode.F100_ALARMLIST_400_2);
        } catch (Exception e) {
            throw new BusinessException(AlarmErrorCode.F100_ALARMLIST_400_2);
        }
    }

    private record CursorPayload(String updatedAt, Long reminderId) {
    }
}
