package com.example.aicontrol.api.controller;


import com.example.aicontrol.api.dto.request.RefundRequest;
import com.example.aicontrol.api.exception.GlobalExceptionHandler;
import com.example.aicontrol.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RefundController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class RefundControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String API_URL = "/api/refunds/analyze";

    private ResultActions assertNoLeak(ResultActions actions) throws Exception {
        return actions
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("com.example"))))
                .andExpect(content().string(not(containsString("api.controller"))))
                .andExpect(content().string(not(containsString("api.dto"))));
    }

    @Test
    void testValidRequest_Returns200() throws Exception {
        RefundRequest request = new RefundRequest("ORD-123", new BigDecimal("150.00"), "Hàng lỗi", "0912345678");

        mockMvc.perform(post(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.message").value("Đã tiếp nhận yêu cầu hoàn tiền cho đơn hàng ORD-123"));
    }

    @Test
    void testValidRequestWithoutCustomerPhone_Returns200() throws Exception {
        RefundRequest request = new RefundRequest("ORD-123", new BigDecimal("150.00"), "Hàng lỗi", null);

        mockMvc.perform(post(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.message").value("Đã tiếp nhận yêu cầu hoàn tiền cho đơn hàng ORD-123"));
    }

    @Test
    void testValidRequestCustomerPhoneOmittedInJson_Returns200() throws Exception {
        String rawJson = "{\"orderId\":\"ORD-123\",\"orderAmount\":150.00,\"reason\":\"Hàng lỗi\"}";

        mockMvc.perform(post(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.message").value("Đã tiếp nhận yêu cầu hoàn tiền cho đơn hàng ORD-123"));
    }

    @Test
    void testMissingOrderId_Returns400() throws Exception {
        RefundRequest request = new RefundRequest("", new BigDecimal("150.00"), "Hàng lỗi", "0912345678");

        ResultActions actions = mockMvc.perform(post(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[*].field").value(hasItem("orderId")));

        assertNoLeak(actions);
    }

    @Test
    void testMissingOrderAmount_Returns400() throws Exception {
        RefundRequest request = new RefundRequest("ORD-123", null, "Hàng lỗi", "0912345678");

        ResultActions actions = mockMvc.perform(post(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[*].field").value(hasItem("orderAmount")));

        assertNoLeak(actions);
    }

    @Test
    void testOrderAmountOmittedInJson_Returns400() throws Exception {
        String rawJson = "{\"orderId\":\"ORD-1\",\"reason\":\"Hàng lỗi\"}";

        ResultActions actions = mockMvc.perform(post(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[*].field").value(hasItem("orderAmount")));

        assertNoLeak(actions);
    }

    @Test
    void testOrderAmountNegative_Returns400() throws Exception {
        RefundRequest request = new RefundRequest("ORD-123", new BigDecimal("-10.00"), "Hàng lỗi", "0912345678");

        ResultActions actions = mockMvc.perform(post(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[*].field").value(hasItem("orderAmount")));

        assertNoLeak(actions);
    }

    @Test
    void testOrderAmountZero_Returns400() throws Exception {
        RefundRequest request = new RefundRequest("ORD-123", BigDecimal.ZERO, "Hàng lỗi", "0912345678");

        ResultActions actions = mockMvc.perform(post(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[*].field").value(hasItem("orderAmount")));

        assertNoLeak(actions);
    }

    @Test
    void testReasonTooLong_Returns400() throws Exception {
        String longReason = "a".repeat(1001);
        RefundRequest request = new RefundRequest("ORD-123", new BigDecimal("150.00"), longReason, "0912345678");

        ResultActions actions = mockMvc.perform(post(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[*].field").value(hasItem("reason")));

        assertNoLeak(actions);
    }

    @Test
    void testReasonBlank_Returns400() throws Exception {
        RefundRequest request = new RefundRequest("ORD-123", new BigDecimal("150.00"), "   ", "0912345678");

        ResultActions actions = mockMvc.perform(post(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[*].field").value(hasItem("reason")));

        assertNoLeak(actions);
    }

    @Test
    void testCustomerPhoneInvalidFormat_Returns400() throws Exception {
        RefundRequest request = new RefundRequest("ORD-123", new BigDecimal("150.00"), "Hàng lỗi", "abc1234567");

        ResultActions actions = mockMvc.perform(post(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[*].field").value(hasItem("customerPhone")));

        assertNoLeak(actions);
    }

    @Test
    void testCustomerPhoneWithPipeChar_Returns400() throws Exception {
        RefundRequest request = new RefundRequest("ORD-123", new BigDecimal("150.00"), "Hàng lỗi", "0|12345678");

        ResultActions actions = mockMvc.perform(post(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[*].field").value(hasItem("customerPhone")));

        assertNoLeak(actions);
    }

    @Test
    void testInvalidJsonBody_Returns400() throws Exception {
        String malformedJson = "{ \"orderId\": \"ORD-123\", \"orderAmount\": }";

        ResultActions actions = mockMvc.perform(post(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));

        assertNoLeak(actions);
    }
}
