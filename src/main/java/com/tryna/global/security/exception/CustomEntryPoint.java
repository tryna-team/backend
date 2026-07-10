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

        // 1. Filter에서 setAttribute("exception", ErrorCode) 한 값을 가져옴
        Object exception = request.getAttribute("exception");

        // 2. ErrorCode 타입이면 사용하고, 아니면 기본 AUTH_401 사용
        ErrorCode code = (exception instanceof ErrorCode) ? (ErrorCode) exception : AuthErrorCode.AUTH_401;

        ObjectMapper objectMapper = new ObjectMapper();

        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(code.getHttpStatus().value());

        // 3. 구체적인 에러 코드(code)를 응답에 반영
        ApiResponse<Void> errorResponse = ApiResponse.fail(code);

        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
