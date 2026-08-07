package com.tryna.global.security.exception;

import com.tryna.global.exception.CommonErrorCode;
import com.tryna.global.exception.ErrorCode;
import com.tryna.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class CustomAccessDenied implements AccessDeniedHandler {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        ErrorCode code = CommonErrorCode.COMMON_403;

        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(code.getHttpStatus().value());

        ApiResponse<Void> errorResponse = ApiResponse.fail(code);

        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
