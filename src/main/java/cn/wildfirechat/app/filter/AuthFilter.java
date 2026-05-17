package cn.wildfirechat.app.filter;

import cn.wildfirechat.app.dto.ErrorCode;
import cn.wildfirechat.app.dto.Result;
import cn.wildfirechat.app.service.SessionService;
import cn.wildfirechat.pojos.OutputApplicationUserInfo;
import cn.wildfirechat.sdk.RobotService;
import cn.wildfirechat.sdk.model.IMResult;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class AuthFilter implements Filter {
    private static final Logger LOG = LoggerFactory.getLogger(AuthFilter.class);

    public static final String USER_ID_KEY = "userId";
    public static final String SESSION_COOKIE_NAME = "minutes_session";

    @Autowired
    private SessionService sessionService;

    @Autowired
    private RobotService robotService;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 放行 OPTIONS 预检请求
        if ("OPTIONS".equals(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String requestURI = httpRequest.getRequestURI();
        LOG.debug("AuthFilter processing request: {}", requestURI);

        // 放行 web 登录接口、配置接口和静态资源
        if (requestURI.equals("/webapi/login") || requestURI.equals("/webapi/config") || requestURI.startsWith("/web/")) {
            chain.doFilter(request, response);
            return;
        }

        // 1. 优先从 Header 获取 authToken
        String authToken = httpRequest.getHeader("authToken");
        if (authToken != null && !authToken.isEmpty()) {
            SessionService.SessionInfo sessionInfo = sessionService.getSession(authToken);
            if (sessionInfo != null) {
                httpRequest.setAttribute(USER_ID_KEY, sessionInfo.getUserId());
                LOG.debug("Auth success via authToken header, userId: {}", sessionInfo.getUserId());
                chain.doFilter(request, response);
                return;
            }
            LOG.warn("Invalid authToken in request: {}", requestURI);
        }

        // 2. 尝试从 cookie 获取 session（兼容 fallback）
        Cookie[] cookies = httpRequest.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (SESSION_COOKIE_NAME.equals(cookie.getName())) {
                    SessionService.SessionInfo sessionInfo = sessionService.getSession(cookie.getValue());
                    if (sessionInfo != null) {
                        httpRequest.setAttribute(USER_ID_KEY, sessionInfo.getUserId());
                        LOG.debug("Auth success via cookie session, userId: {}", sessionInfo.getUserId());
                        chain.doFilter(request, response);
                        return;
                    }
                }
            }
        }

        // 3. 尝试从 Header 获取 authCode（兼容原有 API 调用方式）
        String authCode = httpRequest.getHeader("authCode");
        if (authCode != null && !authCode.isEmpty()) {
            String userId = validateAuthCode(authCode);
            if (userId != null) {
                httpRequest.setAttribute(USER_ID_KEY, userId);
                LOG.debug("Auth success via authCode header, userId: {}", userId);
                chain.doFilter(request, response);
                return;
            }
            LOG.warn("Invalid authCode in request: {}", requestURI);
            writeErrorResponse(httpResponse, ErrorCode.AUTH_CODE_INVALID);
            return;
        }

        LOG.warn("Missing authCode or session cookie in request: {}", requestURI);
        writeErrorResponse(httpResponse, ErrorCode.AUTH_CODE_MISSING);
    }

    private static final Gson GSON = new Gson();

    private void writeErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(GSON.toJson(Result.error(errorCode.getCode(), errorCode.getMessage())));
    }

    private String validateAuthCode(String authCode) {
        LOG.info("Validate authCode with RobotService, authCode: {}", authCode);
        try {
            IMResult<OutputApplicationUserInfo> imResult = robotService.applicationGetUserInfo(authCode);
            if (imResult != null && imResult.getErrorCode() == cn.wildfirechat.common.ErrorCode.ERROR_CODE_SUCCESS) {
                return imResult.getResult().getUserId();
            }
        } catch (Exception e) {
            LOG.error("Failed to validate authCode", e);
        }
        return null;
    }
}
