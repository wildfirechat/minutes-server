package cn.wildfirechat.app.filter;

import cn.wildfirechat.app.dto.ErrorCode;
import cn.wildfirechat.pojos.OutputApplicationUserInfo;
import cn.wildfirechat.sdk.UserAdmin;
import cn.wildfirechat.sdk.model.IMResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class AuthFilter implements Filter {
    private static final Logger LOG = LoggerFactory.getLogger(AuthFilter.class);

    public static final String USER_ID_KEY = "userId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        if ("OPTIONS".equals(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String requestURI = httpRequest.getRequestURI();
        LOG.debug("AuthFilter processing request: {}", requestURI);

        String authCode = httpRequest.getHeader("authCode");
        if (authCode == null || authCode.isEmpty()) {
            LOG.warn("Missing authCode in request: {}", requestURI);
            writeErrorResponse(httpResponse, ErrorCode.AUTH_CODE_MISSING);
            return;
        }

        String userId = validateAuthCode(authCode);
        if (userId == null) {
            LOG.warn("Invalid authCode: {}", authCode);
            writeErrorResponse(httpResponse, ErrorCode.AUTH_CODE_INVALID);
            return;
        }

        httpRequest.setAttribute(USER_ID_KEY, userId);
        LOG.debug("Auth success, userId: {}", userId);

        chain.doFilter(request, response);
    }

    private void writeErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(String.format("{\"code\":%d,\"message\":\"%s\"}",
                errorCode.getCode(), errorCode.getMessage()));
    }

    private String validateAuthCode(String authCode) {
        LOG.info("Validate authCode with IM service, authCode: {}", authCode);
        try {
            IMResult<OutputApplicationUserInfo> imResult = UserAdmin.applicationGetUserInfo(authCode);
            if (imResult != null && imResult.getErrorCode() == cn.wildfirechat.common.ErrorCode.ERROR_CODE_SUCCESS) {
                return imResult.getResult().getUserId();
            }
        } catch (Exception e) {
            LOG.error("Failed to validate authCode", e);
        }
        return null;
    }
}
