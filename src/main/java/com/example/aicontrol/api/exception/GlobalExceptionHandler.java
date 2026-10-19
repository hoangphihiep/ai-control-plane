package com.example.aicontrol.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        // Trích xuất danh sách các lỗi từ exception và map sang định dạng trung tính
        List<ErrorDetail> details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> new ErrorDetail(
                        fieldError.getField(),
                        fieldError.getDefaultMessage()
                ))
                .collect(Collectors.toList());

        // Tạo đối tượng phản hồi lỗi
        ErrorResponse response = new ErrorResponse("VALIDATION_FAILED", details);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        // Xử lý khi body request không thể phân tích (JSON hỏng / sai cú pháp)
        ErrorResponse response = new ErrorResponse("MALFORMED_REQUEST", List.of());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // Các record DTO nội bộ dùng để định dạng JSON trả về
    public record ErrorResponse(
            String error,
            List<ErrorDetail> details
    ) {}

    public record ErrorDetail(
            String field,
            String message
    ) {}
}
