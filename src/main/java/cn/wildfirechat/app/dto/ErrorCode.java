package cn.wildfirechat.app.dto;

public enum ErrorCode {
    SUCCESS(0, "success"),

    SYSTEM_ERROR(1, "系统错误"),
    PARAM_ERROR(2, "参数错误"),
    UNKNOWN_ERROR(999, "未知错误"),

    UNAUTHORIZED(1000, "未授权"),
    AUTH_CODE_MISSING(1001, "缺少authCode"),
    AUTH_CODE_INVALID(1002, "无效的authCode"),
    NOT_FOUND(1003, "记录不存在");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
