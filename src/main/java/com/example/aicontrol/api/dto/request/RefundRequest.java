package com.example.aicontrol.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RefundRequest(
        @NotBlank(message = "Mã đơn hàng không được để trống")
        String orderId,

        @NotNull(message = "Số tiền hoàn không được để trống")
        @Positive(message = "Số tiền hoàn phải là số dương")
        BigDecimal orderAmount,

        @NotBlank(message = "Lý do hoàn tiền không được để trống")
        @Size(max = 1000, message = "Lý do hoàn tiền tối đa 1000 ký tự")
        String reason,

        // Pattern cho số điện thoại Việt Nam: bắt đầu bằng 0 hoặc +84, tiếp theo là các đầu số 3, 5, 7, 8, 9 và 8 chữ số cuối
        @Pattern(regexp = "^(0|\\+84)[35789]\\d{8}$", message = "Số điện thoại không đúng định dạng Việt Nam")
        String customerPhone
) {
}
