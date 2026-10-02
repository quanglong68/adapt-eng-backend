package com.longdq.adaptengbackend.common.exception;

import com.longdq.adaptengbackend.common.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import com.longdq.adaptengbackend.common.enums.Level;
import com.longdq.adaptengbackend.modules.user.entity.User;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 400 — lỗi nghiệp vụ (bao gồm ValidationException, QuotaExceededException mapping 429 qua status riêng)
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {
        log.warn("Business exception at {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(ex.getStatus(), ex.getMessage(), request, null, null);
    }

    // 401 — sai email/mật khẩu
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest request) {
        log.warn("Authentication failed at {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng!", request, null, null);
    }

    // 401 — các lỗi xác thực khác (token hết hạn, token sai...)
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication error at {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.", request, null, null);
    }

    // 403 — không đủ quyền. Giữ nguyên message nghiệp vụ (REQUIRE_VIP, REQUIRE_WRITING_PLACEMENT_TEST...)
    // để FE nhận diện và hiển thị đúng màn hình, thay vì ghi đè message chung chung.
    @ExceptionHandler({AccessDeniedException.class, ForbiddenException.class})
    public ResponseEntity<ErrorResponse> handleForbidden(
            RuntimeException ex, HttpServletRequest request) {
        log.warn("Forbidden at {}: {}", request.getRequestURI(), ex.getMessage());
        String message = (ex.getMessage() == null || ex.getMessage().isBlank())
                ? "Bạn không có quyền thực hiện thao tác này."
                : ex.getMessage();
        return buildResponse(HttpStatus.FORBIDDEN, message, request, null, null);
    }

    // 404 — không tìm thấy user khi đăng nhập
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUsernameNotFound(
            UsernameNotFoundException ex, HttpServletRequest request) {
        log.warn("User not found at {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request, null, null);
    }

    // 400 — @Valid @RequestBody fail: trả chi tiết từng field.
    // Message được gom trùng + tóm tắt để FE không phải hiển thị cả tràng dài.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        FieldError::getDefaultMessage,
                        (first, second) -> first));
        String message = summarizeErrors(errors);
        log.warn("Validation failed at {}: {}", request.getRequestURI(), message);
        return buildResponse(HttpStatus.BAD_REQUEST, message, request, errors, null);
    }

    // 400 — @Validated trên @RequestParam/@PathVariable fail
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> errors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        violation -> violation.getMessage(),
                        (first, second) -> first));
        String message = summarizeErrors(errors);
        log.warn("Constraint violation at {}: {}", request.getRequestURI(), message);
        return buildResponse(HttpStatus.BAD_REQUEST, message, request, errors, null);
    }

    // 400 — JSON sai format / enum sai giá trị (ví dụ Level không tồn tại)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Malformed request at {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "Dữ liệu gửi lên không đúng định dạng. Vui lòng kiểm tra lại.", request, null, null);
    }

    // 400 — sai kiểu @PathVariable/@RequestParam (ví dụ /test/generate/ABC)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn("Type mismatch at {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST,
                "Giá trị '" + ex.getValue() + "' không hợp lệ cho tham số '" + ex.getName() + "'.",
                request, null, null);
    }

    // 400 — thiếu @RequestParam bắt buộc
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.warn("Missing parameter at {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST,
                "Thiếu tham số bắt buộc: '" + ex.getParameterName() + "'.", request, null, null);
    }

    // 500 — lỗi chưa phân loại: che message nội bộ, cấp errorId để trace log
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(
            RuntimeException ex, HttpServletRequest request) {
        String errorId = UUID.randomUUID().toString().substring(0, 8);
        log.error("Unhandled runtime exception at {} [errorId={}]: {}", request.getRequestURI(), errorId, ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "Đã xảy ra lỗi hệ thống [mã lỗi: " + errorId + "]. Vui lòng thử lại sau.",
                request, null, errorId);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        String errorId = UUID.randomUUID().toString().substring(0, 8);
        log.error("Unexpected exception at {} [errorId={}]: {}", request.getRequestURI(), errorId, ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "Đã xảy ra lỗi hệ thống [mã lỗi: " + errorId + "]. Vui lòng thử lại sau.",
                request, null, errorId);
    }

    // Gom các message trùng nhau: "A; A; A" -> "A (+2 lỗi tương tự)".
    private String summarizeErrors(Map<String, String> errors) {
        java.util.List<String> distinct = errors.values().stream().distinct().toList();
        if (distinct.isEmpty()) {
            return "Dữ liệu gửi lên chưa hợp lệ.";
        }
        if (distinct.size() == 1 && errors.size() > 1) {
            return distinct.get(0) + " (+" + (errors.size() - 1) + " lỗi tương tự)";
        }
        return String.join("; ", distinct);
    }

    private ResponseEntity<ErrorResponse> buildResponse(            HttpStatus status, String message, HttpServletRequest request,
            Map<String, String> errors, String errorId) {
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .message(message)
                .path(request.getRequestURI())
                .errors(errors)
                .errorId(errorId)
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
