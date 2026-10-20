package com.example.aicontrol.api.controller;

import com.example.aicontrol.api.dto.request.RefundRequest;
import com.example.aicontrol.api.dto.response.RefundResponse;
import com.example.aicontrol.controlplane.sanitizer.PromptInjectionDetector;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/refunds")
public class RefundController {

    private final PromptInjectionDetector promptInjectionDetector;

    public RefundController(PromptInjectionDetector promptInjectionDetector) {
        this.promptInjectionDetector = promptInjectionDetector;
    }

    @PostMapping("/analyze")
    public ResponseEntity<RefundResponse> analyzeRefund(@Valid @RequestBody RefundRequest request) {

        // TODO: Bước 10 chuyển vào AiControlPlaneService
        promptInjectionDetector.detect(request.reason());

        // Tạm thời trả về phản hồi với status "RECEIVED"
        RefundResponse response = new RefundResponse(
                "RECEIVED",
                "Đã tiếp nhận yêu cầu hoàn tiền cho đơn hàng " + request.orderId()
        );

        return ResponseEntity.ok(response);
    }
}
