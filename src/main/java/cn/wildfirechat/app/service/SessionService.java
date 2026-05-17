package cn.wildfirechat.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionService {
    private static final Logger LOG = LoggerFactory.getLogger(SessionService.class);
    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000; // 30分钟

    private final ConcurrentHashMap<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    public String createSession(String userId, String displayName, String portraitUrl) {
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, new SessionInfo(userId, displayName, portraitUrl, System.currentTimeMillis()));
        LOG.info("Created session for userId={}, token={}", userId, token);
        return token;
    }

    public SessionInfo getSession(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        SessionInfo info = sessions.get(token);
        if (info == null) {
            return null;
        }
        if (System.currentTimeMillis() - info.createTime > SESSION_TIMEOUT_MS) {
            sessions.remove(token);
            LOG.info("Session expired, token={}", token);
            return null;
        }
        return info;
    }

    public SessionInfo getSessionByUserId(String userId) {
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        long now = System.currentTimeMillis();
        for (java.util.Map.Entry<String, SessionInfo> entry : sessions.entrySet()) {
            SessionInfo info = entry.getValue();
            if (userId.equals(info.getUserId()) && (now - info.getCreateTime() <= SESSION_TIMEOUT_MS)) {
                return info;
            }
        }
        return null;
    }

    @Scheduled(fixedRate = 60000) // 每分钟清理一次过期session
    public void cleanExpiredSessions() {
        long now = System.currentTimeMillis();
        int count = 0;
        for (java.util.Map.Entry<String, SessionInfo> entry : sessions.entrySet()) {
            if (now - entry.getValue().createTime > SESSION_TIMEOUT_MS) {
                sessions.remove(entry.getKey());
                count++;
            }
        }
        if (count > 0) {
            LOG.info("Cleaned {} expired sessions", count);
        }
    }

    public static class SessionInfo {
        private final String userId;
        private final String displayName;
        private final String portraitUrl;
        private final long createTime;

        public SessionInfo(String userId, String displayName, String portraitUrl, long createTime) {
            this.userId = userId;
            this.displayName = displayName;
            this.portraitUrl = portraitUrl;
            this.createTime = createTime;
        }

        public String getUserId() {
            return userId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getPortraitUrl() {
            return portraitUrl;
        }

        public long getCreateTime() {
            return createTime;
        }
    }
}
