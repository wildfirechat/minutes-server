package cn.wildfirechat.app.exception;

import cn.wildfirechat.app.dto.ErrorCode;
import cn.wildfirechat.app.dto.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        LOG.warn("业务异常: {}", e.getMessage());
        return Result.error(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        LOG.error("系统异常", e);
        return Result.error(ErrorCode.SYSTEM_ERROR, "系统异常: " + e.getMessage());
    }
}
