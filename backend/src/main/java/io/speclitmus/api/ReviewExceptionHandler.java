package io.speclitmus.api;

import io.speclitmus.ai.AiModelTimeoutException;
import io.speclitmus.ai.AiModelUnavailableException;
import io.speclitmus.ai.StubInputUnsupportedException;
import io.speclitmus.review.InvalidModelOutputException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ReviewExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ReviewExceptionHandler.class);

    @ExceptionHandler(ReviewRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(ReviewRequestException exception) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", exception.getMessage(), false));
    }

    @ExceptionHandler(StubInputUnsupportedException.class)
    public ResponseEntity<ApiError> handleStubInputUnsupported() {
        return ResponseEntity.badRequest().body(new ApiError("FIXTURE_INPUT_UNSUPPORTED",
                "当前后端处于固定测试模式，仅支持内置示例。审查自定义需求请以真实模型模式启动后端。", false));
    }

    @ExceptionHandler(InvalidModelOutputException.class)
    public ResponseEntity<ApiError> handleInvalidModelOutput(InvalidModelOutputException exception) {
        logger.warn("Model review output was rejected: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError("AI_OUTPUT_INVALID", "模型返回的结果暂不可用，请重试。", true));
    }

    @ExceptionHandler(AiModelTimeoutException.class)
    public ResponseEntity<ApiError> handleModelTimeout() {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(new ApiError("AI_TIMEOUT", "模型响应超时，请重试。", true));
    }

    @ExceptionHandler(AiModelUnavailableException.class)
    public ResponseEntity<ApiError> handleModelUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("AI_UNAVAILABLE", "模型服务未配置或暂不可用。", true));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedFailure() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "请求处理失败，请稍后重试。", false));
    }
}
