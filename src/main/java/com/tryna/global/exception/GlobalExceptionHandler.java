package com.tryna.global.exception;

import com.tryna.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.fail(errorCode));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e,
            HttpServletRequest request
    ) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();

        if (requestUri.startsWith(contextPath)) {
            requestUri = requestUri.substring(contextPath.length());
        }
        
        String requestMethod = request.getMethod();

        if ("POST".equalsIgnoreCase(requestMethod)
                && "/api/v1/labels".equals(requestUri)) {
            return ResponseEntity
                    .status(LabelErrorCode.B108_LABEL_CREATE_400.getHttpStatus())
                    .body(ApiResponse.fail(
                            LabelErrorCode.B108_LABEL_CREATE_400
                    ));
        }

        return ResponseEntity
                .status(CommonErrorCode.COMMON_400.getHttpStatus())
                .body(ApiResponse.fail(
                        CommonErrorCode.COMMON_400
                ));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequestException(Exception e, HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();

        // URI에서 Context Path 제거 보장
        if (requestUri.startsWith(contextPath)) {
            requestUri = requestUri.substring(contextPath.length());
        }

        String method = request.getMethod();
        ErrorCode errorCode = CommonErrorCode.COMMON_400;

        // API 명세서 기준 400 에러 코드 세분화
        if ("/api/v1/auth-sessions".equals(requestUri) && "POST".equalsIgnoreCase(method)) {
            errorCode = AuthErrorCode.A105_AUTH_SESSION_400;
        } else if ("/api/v1/auth-sessions/permissions".equals(requestUri)) {
            errorCode = AuthErrorCode.A104_PERMISSION_CHECK_400;
        } else if ("/api/v1/users/conversions".equals(requestUri)) {
            errorCode = AuthErrorCode.A106_USER_CONVERSION_400;
        } else if ("/api/v1/auth-sessions/refresh".equals(requestUri)) {
            errorCode = AuthErrorCode.A108_AUTH_REFRESH_400;
        } else if ("/api/v1/auth-sessions/me".equals(requestUri) && "DELETE".equalsIgnoreCase(method)) {
            errorCode = AuthErrorCode.A109_AUTH_LOGOUT_400;
        } else if ("/api/v1/guests".equals(requestUri) && "POST".equalsIgnoreCase(method)) {
            errorCode = UserErrorCode.A102_GUEST_CREATE_400;
        }

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.fail(errorCode));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        return ResponseEntity
                .status(CommonErrorCode.COMMON_500.getHttpStatus())
                .body(ApiResponse.fail(CommonErrorCode.COMMON_500));
    }
}
