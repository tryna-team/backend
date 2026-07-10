package com.tryna.global.security.exception;

import com.tryna.global.exception.AuthErrorCode;
import com.tryna.global.exception.ErrorCode;
import com.tryna.global.response.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;


public class CustomEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {

        ObjectMapper objectMapper = new ObjectMapper();

        ErrorCode code = AuthErrorCode.AUTH_401;

        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(code.getHttpStatus().value());

        ApiResponse<Void> errorResponse = ApiResponse.fail(code);

        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
