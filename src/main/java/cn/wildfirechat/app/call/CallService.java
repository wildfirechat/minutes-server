package cn.wildfirechat.app.call;

import cn.wildfirechat.*;

import cn.wildfirechat.app.entity.ConferenceParticipant;
import cn.wildfirechat.app.llm.MeetingSummaryService;
import cn.wildfirechat.app.repository.ConferenceParticipantRepository;
import cn.wildfirechat.app.repository.TranscriptionRecordRepository;
import cn.wildfirechat.pojos.Conversation;
import cn.wildfirechat.sdk.RobotService;
import cn.wildfirechat.sdk.UserAdmin;
import cn.wildfirechat.sdk.model.IMResult;
import dev.onvoid.webrtc.media.video.VideoTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@org.springframework.stereotype.Service
public class CallService {
    private static final Logger LOG = LoggerFactory.getLogger(CallService.class);
    @Value("${robot.im_url}")
    private String robotImUrl;

    @Value("${robot.im_id}")
    private String robotImId;

    @Value("${robot.im_secret}")
    private String robotImSecret;

    @Value("${asr.ws.url}")
    private String mWebsocketUrl;

    @Value("${janus.ip.replace.map:}")
    private String janusIpReplaceMap;

    @Autowired
    private TranscriptionRecordRepository transcriptionRecordRepository;

    @Autowired
    private ConferenceParticipantRepository conferenceParticipantRepository;

    @Autowired
    private MeetingSummaryService meetingSummaryService;

    private RobotService robotService;

    private final Map<String, CallSession> sessionMap = new ConcurrentHashMap<>();

    @PostConstruct
    private void init() {
        this.robotService = new RobotService(robotImUrl, robotImId, robotImSecret);
        //1. 初始化音视频SDK
        AVEngineKit.getInstance().init(robotService, null);

        //2. 设置Janus公网IP到内网IP的替换映射
        if (StringUtils.hasText(janusIpReplaceMap)) {
            Map<String, String> ipReplaceMap = new HashMap<>();
            String[] pairs = janusIpReplaceMap.split(",");
            for (String pair : pairs) {
                String[] parts = pair.split(":");
                if (parts.length == 2) {
                    ipReplaceMap.put(parts[0].trim(), parts[1].trim());
                }
            }
            if (!ipReplaceMap.isEmpty()) {
                AVEngineKit.getInstance().setRemoteSdpIpReplaceMap(ipReplaceMap);
                LOG.info("Set Janus remote SDP IP replace map: {}", ipReplaceMap);
            }
        }

        //3. 打开webrtc的日志，一般不用打开，除非出现问题需要debug
//        AVEngineKit.getInstance().enableWebRTCLog();
    }


    public void joinConference(String conferenceId, String pin, boolean advance) {
        if(sessionMap.containsKey(conferenceId)) {
            return;
        }

        Conversation conversation = new Conversation(2, conferenceId, 0);
        CallSession callSession = AVEngineKit.getInstance().joinConference(conferenceId, pin, true, advance, true, new AsrAudioDevice(conversation, conferenceId, robotImId, robotService, transcriptionRecordRepository, mWebsocketUrl), new CallEventCallback() {
            @Override
            public void onCallStateUpdated(CallSession callSession, CallState state) {

            }

            @Override
            public void onParticipantJoined(CallSession callSession, String userId) {
                recordParticipant(conferenceId, userId);
            }

            @Override
            public void onParticipantConnected(CallSession callSession, String userId) {
                recordParticipant(conferenceId, userId);
            }

            @Override
            public void onReceiveRemoteVideoTrack(CallSession callSession, String userId, VideoTrack videoTrack) {

            }

            @Override
            public void onParticipantLeft(CallSession callSession, String userId, CallEndReason reason) {
                if(callSession.getParticipants().isEmpty()) {
                    callSession.endCall();
                }
            }

            @Override
            public void onCallEnd(CallSession callSession, CallEndReason endReason) {
                sessionMap.remove(conferenceId);
                meetingSummaryService.generateAndSendSummary(conferenceId, robotService, robotImId);
            }
        });
        sessionMap.put(conferenceId, callSession);
    }

    private void recordParticipant(String conferenceId, String userId) {
        if (conferenceParticipantRepository == null || StringUtils.isEmpty(userId)) {
            return;
        }
        try {
            boolean hasPermission = checkParticipantPermission(userId);

            ConferenceParticipant participant = new ConferenceParticipant();
            participant.setConferenceId(conferenceId);
            participant.setUserId(userId);
            participant.setHasPermission(hasPermission);
            participant.setJoinedAt(new Date());
            conferenceParticipantRepository.save(participant);

            LOG.info("Recorded participant conferenceId={}, userId={}, hasPermission={}", conferenceId, userId, hasPermission);
        } catch (Exception e) {
            LOG.error("Failed to record participant", e);
        }
    }

    private boolean checkParticipantPermission(String userId) {
        try {
            IMResult<cn.wildfirechat.pojos.InputOutputUserInfo> imResult = UserAdmin.getUserByUserId(userId);
            if (imResult != null && imResult.getErrorCode() == cn.wildfirechat.common.ErrorCode.ERROR_CODE_SUCCESS) {
                return imResult.getResult() != null;
            }
        } catch (Exception e) {
            LOG.error("Failed to check participant permission for userId: {}", userId, e);
        }
        return false;
    }

    public void onConferenceEvent(String event) {
        AVEngineKit.getInstance().onConferenceEvent(event);
    }
}
