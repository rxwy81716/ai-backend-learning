package com.jianbo.localaiknowledge.config;

import com.jianbo.localaiknowledge.utils.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import jakarta.servlet.http.HttpServletResponse;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 全局响应统一处理
 *
 * 功能：
 * 1. 将所有返回值统一包装为 { code, message, data } 结构
 * 2. 记录接口响应日志
 * 3. 处理异常响应
 */
@Slf4j
@RestControllerAdvice
public class ResponseAdvice implements ResponseBodyAdvice<Object> {

    /**
     * 需要忽略统一包装的路径
     */
    private static final String[] IGNORE_PATHS = {
            "/swagger-ui",
            "/v3/api-docs",
            "/favicon.ico",
            "/stream"          // SSE 流式接口
    };

    /**
     * 需要忽略统一包装的 Content-Type
     */
    private static final MediaType STREAM_MEDIA_TYPE = MediaType.TEXT_EVENT_STREAM;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 排除 Flux 流式返回类型（SSE 接口）
        if (Flux.class.isAssignableFrom(returnType.getParameterType())) {
            return false;
        }
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                   org.springframework.http.MediaType selectedContentType,
                                   Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                   ServerHttpRequest request, ServerHttpResponse response) {

        // 获取请求路径
        String path = request.getURI().getPath();

        // 忽略特定路径
        for (String ignorePath : IGNORE_PATHS) {
            if (path.contains(ignorePath)) {
                return body;
            }
        }

        // 忽略 SSE 流式响应（Content-Type 为 text/event-stream）
        if (selectedContentType != null && STREAM_MEDIA_TYPE.includes(selectedContentType)) {
            return body;
        }

        // 如果已经是 R 类型，直接返回
        if (body instanceof R) {
            return body;
        }

        // 如果是 ResponseEntity，直接处理
        if (body instanceof ResponseEntity) {
            return handleResponseEntity((ResponseEntity<?>) body, request, response);
        }

        // 获取 HTTP 状态码
        HttpStatus status = getHttpStatus(response);

        // 根据状态码构建响应
        if (status.is2xxSuccessful()) {
            return R.ok(body);
        } else if (status.is4xxClientError()) {
            return R.badRequest(body != null ? body.toString() : "请求错误");
        } else if (status.is5xxServerError()) {
            return R.serverError(body != null ? body.toString() : "服务器错误");
        }

        return R.ok(body);
    }

    /**
     * 处理 ResponseEntity 类型
     */
    private Object handleResponseEntity(ResponseEntity<?> responseEntity,
                                         ServerHttpRequest request,
                                         ServerHttpResponse response) {
        HttpStatusCode status = responseEntity.getStatusCode();
        Object body = responseEntity.getBody();

        // 204 No Content
        if (status == HttpStatus.NO_CONTENT) {
            return R.ok();
        }

        // 2xx 成功
        if (status.is2xxSuccessful()) {
            if (body instanceof R) {
                return body;
            }
            return R.ok(body);
        }

        // 4xx 客户端错误
        if (status.is4xxClientError()) {
            String message = extractMessage(body);
            if (message != null) {
                return R.badRequest(message);
            }
            return R.badRequest("请求错误 [" + status.value() + "]");
        }

        // 5xx 服务器错误
        if (status.is5xxServerError()) {
            String message = extractMessage(body);
            if (message != null) {
                return R.serverError(message);
            }
            return R.serverError("服务器错误 [" + status.value() + "]");
        }

        // 其他状态码
        return R.ok(body);
    }

    /**
     * 获取 HTTP 状态码
     */
    private HttpStatus getHttpStatus(ServerHttpResponse response) {
        if (response instanceof ServletServerHttpResponse) {
            HttpServletResponse servletResponse = ((ServletServerHttpResponse) response).getServletResponse();
            return HttpStatus.valueOf(servletResponse.getStatus());
        }
        return HttpStatus.OK;
    }

    /**
     * 从响应体中提取消息
     */
    private String extractMessage(Object body) {
        if (body == null) {
            return null;
        }
        if (body instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) body;
            Object error = map.get("error");
            if (error != null) {
                return error.toString();
            }
            Object message = map.get("message");
            if (message != null) {
                return message.toString();
            }
        }
        return body.toString();
    }
}
