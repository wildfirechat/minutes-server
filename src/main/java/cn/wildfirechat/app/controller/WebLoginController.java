package cn.wildfirechat.app.controller;

import cn.wildfirechat.app.dto.ErrorCode;
import cn.wildfirechat.app.dto.Result;
import cn.wildfirechat.app.dto.web.LoginRequest;
import cn.wildfirechat.app.dto.web.LoginResponse;
import cn.wildfirechat.app.dto.web.WebConfigResponse;
import cn.wildfirechat.app.dto.web.MeetingDetailDTO;
import cn.wildfirechat.app.dto.web.TranscriptionDTO;
import cn.wildfirechat.app.entity.ConferenceParticipant;
import cn.wildfirechat.app.entity.MeetingSummary;
import cn.wildfirechat.app.entity.TranscriptionRecord;
import cn.wildfirechat.app.exception.BizException;
import cn.wildfirechat.app.filter.AuthFilter;
import cn.wildfirechat.app.repository.ConferenceParticipantRepository;
import cn.wildfirechat.app.repository.MeetingSummaryRepository;
import cn.wildfirechat.app.repository.TranscriptionRecordRepository;
import cn.wildfirechat.app.service.SessionService;
import cn.wildfirechat.pojos.OutputApplicationConfigData;
import cn.wildfirechat.pojos.OutputApplicationUserInfo;
import cn.wildfirechat.sdk.RobotService;
import cn.wildfirechat.sdk.model.IMResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/webapi")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class WebLoginController {
    private static final Logger LOG = LoggerFactory.getLogger(WebLoginController.class);

    @Value("${robot.im_id}")
    private String robotImId;

    @Value("${audio.base.url:}")
    private String audioBaseUrl;

    @Value("${audio.file.extension:wav}")
    private String audioFileExtension;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private TranscriptionRecordRepository transcriptionRecordRepository;

    @Autowired
    private MeetingSummaryRepository meetingSummaryRepository;

    @Autowired
    private ConferenceParticipantRepository conferenceParticipantRepository;

    @Autowired
    private RobotService robotService;

    /**
     * 安全解码 authCode，兼容 URL 编码未解码的情况
     */
    private String decodeAuthCode(String authCode) {
        if (authCode == null || !authCode.contains("%")) {
            return authCode;
        }
        try {
            // URLDecoder 会把 + 变成空格，先保护 +
            String temp = authCode.replace("+", "@@PLUS@@");
            String decoded = java.net.URLDecoder.decode(temp, "UTF-8");
            return decoded.replace("@@PLUS@@", "+");
        } catch (Exception e) {
            LOG.warn("Failed to decode authCode, use raw value", e);
            return authCode;
        }
    }

    /**
     * 获取 JSSDK 配置
     */
    @GetMapping("/config")
    public Result<WebConfigResponse> getConfig() {
        try {
            OutputApplicationConfigData configData = robotService.getApplicationSignature();
            if (configData == null) {
                LOG.warn("RobotService getApplicationSignature returned null");
                return Result.error(ErrorCode.SYSTEM_ERROR, "获取应用签名失败");
            }
            WebConfigResponse response = new WebConfigResponse(
                configData.getAppId(),
                configData.getAppType(),
                configData.getTimestamp(),
                configData.getNonceStr(),
                configData.getSignature()
            );
            return Result.success(response);
        } catch (Exception e) {
            LOG.error("Get application signature error", e);
            return Result.error(ErrorCode.SYSTEM_ERROR, e.getMessage());
        }
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/account")
    public Result<LoginResponse> getAccount(HttpServletRequest request) {
        String userId = (String) request.getAttribute(AuthFilter.USER_ID_KEY);
        if (userId == null || userId.isEmpty()) {
            return Result.error(ErrorCode.UNAUTHORIZED);
        }
        SessionService.SessionInfo sessionInfo = sessionService.getSessionByUserId(userId);
        if (sessionInfo == null) {
            return Result.error(ErrorCode.UNAUTHORIZED);
        }
        return Result.success(new LoginResponse(
            sessionInfo.getUserId(),
            sessionInfo.getDisplayName(),
            sessionInfo.getPortraitUrl()
        ));
    }

    /**
     * Web 页面登录
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        if (request.getAuthCode() == null || request.getAuthCode().isEmpty()) {
            return Result.error(ErrorCode.PARAM_ERROR, "缺少authCode");
        }

        String authCode = decodeAuthCode(request.getAuthCode());
        LOG.info("Login authCode decoded: {}", authCode);

        try {
            IMResult<OutputApplicationUserInfo> imResult = robotService.applicationGetUserInfo(authCode);
            if (imResult == null || imResult.getErrorCode() != cn.wildfirechat.common.ErrorCode.ERROR_CODE_SUCCESS) {
                LOG.warn("RobotService applicationGetUserInfo failed, code={}", imResult != null ? imResult.getCode() : "null");
                return Result.error(ErrorCode.AUTH_CODE_INVALID);
            }

            OutputApplicationUserInfo userInfo = imResult.getResult();
            String userId = userInfo.getUserId();
            String displayName = userInfo.getDisplayName();
            String portraitUrl = userInfo.getPortraitUrl();

            // 创建 session
            String sessionToken = sessionService.createSession(userId, displayName, portraitUrl);

            // 通过响应头返回 token（前端存储到 localStorage）
            response.setHeader("authToken", sessionToken);

            LOG.info("Web login success, userId={}, displayName={}", userId, displayName);
            return Result.success(new LoginResponse(userId, displayName, portraitUrl));
        } catch (Exception e) {
            LOG.error("Web login error", e);
            return Result.error(ErrorCode.SYSTEM_ERROR, e.getMessage());
        }
    }

    /**
     * 获取会议详情（聚合接口）
     */
    @PostMapping("/meeting/{conferenceId}")
    public Result<MeetingDetailDTO> getMeetingDetail(@PathVariable String conferenceId, HttpServletRequest request) {
        String userId = (String) request.getAttribute(AuthFilter.USER_ID_KEY);
        if (userId == null || userId.isEmpty()) {
            return Result.error(ErrorCode.UNAUTHORIZED);
        }

        // 检查参会者权限
        if (!conferenceParticipantRepository.findByConferenceIdAndUserId(conferenceId, userId).isPresent()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "您不是该会议的参会人员，无权查询");
        }

        MeetingDetailDTO detail = new MeetingDetailDTO();
        detail.setConferenceId(conferenceId);

        // 会议纪要
        MeetingSummary summary = meetingSummaryRepository.findByConferenceId(conferenceId).orElse(null);
        detail.setSummary(summary != null ? summary.getSummary() : null);

        // 参会者列表
        List<ConferenceParticipant> participants = conferenceParticipantRepository.findByConferenceIdOrderByCreatedAtDesc(conferenceId);
        detail.setParticipants(participants);

        // 转写记录（按时间正序，方便时间轴展示）
        List<TranscriptionRecord> records = transcriptionRecordRepository.findByConferenceIdOrderByCreatedAtAsc(conferenceId);
        List<TranscriptionDTO> transcriptionDTOs = records.stream().map(this::convertToDTO).collect(Collectors.toList());
        detail.setTranscriptions(transcriptionDTOs);

        // 查询所有相关用户的 displayName
        Map<String, String> displayNameMap = new java.util.HashMap<>();
        java.util.Set<String> userIds = new java.util.HashSet<>();
        for (ConferenceParticipant p : participants) {
            userIds.add(p.getUserId());
        }
        for (TranscriptionRecord r : records) {
            userIds.add(r.getUserId());
        }
        for (String uid : userIds) {
            if (uid == null || displayNameMap.containsKey(uid)) continue;
            try {
                IMResult<cn.wildfirechat.pojos.InputOutputUserInfo> userResult = robotService.getUserInfo(uid);
                if (userResult != null && userResult.getErrorCode() == cn.wildfirechat.common.ErrorCode.ERROR_CODE_SUCCESS && userResult.getResult() != null) {
                    String dn = userResult.getResult().getDisplayName();
                    displayNameMap.put(uid, (dn != null && !dn.isEmpty()) ? dn : uid);
                } else {
                    displayNameMap.put(uid, uid);
                }
            } catch (Exception e) {
                displayNameMap.put(uid, uid);
            }
        }
        detail.setUserDisplayNameMap(displayNameMap);

        return Result.success(detail);
    }

    private TranscriptionDTO convertToDTO(TranscriptionRecord record) {
        TranscriptionDTO dto = new TranscriptionDTO();
        dto.setId(record.getId());
        dto.setConferenceId(record.getConferenceId());
        dto.setUserId(record.getUserId());
        dto.setTimestampMs(record.getTimestampMs());
        dto.setDuration(record.getDuration());
        dto.setContent(record.getContent());
        dto.setCorrectedContent(record.getCorrectedContent());
        dto.setSegmentName(record.getSegmentName());
        dto.setMessageId(record.getMessageId());
        dto.setScreenSharing(record.getScreenSharing());
        dto.setCreatedAt(record.getCreatedAt());

        // 拼接音频文件 URL
        if (audioBaseUrl != null && !audioBaseUrl.isEmpty() && record.getSegmentName() != null && !record.getSegmentName().isEmpty()) {
            String base = audioBaseUrl;
            if (!base.endsWith("/")) {
                base = base + "/";
            }
            try {
                String encodedSegment = java.net.URLEncoder.encode(record.getSegmentName(), "UTF-8");
                dto.setAudioUrl(base + encodedSegment + "." + audioFileExtension);
            } catch (java.io.UnsupportedEncodingException e) {
                dto.setAudioUrl(base + record.getSegmentName() + "." + audioFileExtension);
            }
        }

        return dto;
    }
}
