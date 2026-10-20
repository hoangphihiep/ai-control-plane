package com.example.aicontrol.controlplane.pii;

import java.util.Collections;
import java.util.Map;

/**
 * Kết quả sau khi che (mask) thông tin định danh cá nhân (PII).
 *
 * @param maskedText Văn bản đã được thay thế PII bằng placeholder ([PHONE_1], [CARD_1]...)
 * @param mapping    Bảng ánh xạ placeholder -> giá trị gốc, chỉ lưu trong bộ nhớ của request
 */
public record MaskedResult(
        String maskedText,
        Map<String, String> mapping
) {
    public MaskedResult {
        mapping = mapping != null ? Map.copyOf(mapping) : Collections.emptyMap();
    }

    /**
     * Ghi đè toString() tuyệt đối không in ra nội dung của mapping để tránh rò rỉ dữ liệu PII nếu log nhầm object này.
     */
    @Override
    public String toString() {
        return "MaskedResult[maskedText='" + maskedText + "', mappingCount=" + mapping.size() + "]";
    }
}
