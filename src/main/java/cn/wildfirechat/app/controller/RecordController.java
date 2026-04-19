package cn.wildfirechat.app.controller;

import cn.wildfirechat.app.dto.ErrorCode;
import cn.wildfirechat.app.dto.Result;
import cn.wildfirechat.app.entity.ConferenceParticipant;
import cn.wildfirechat.app.entity.MeetingSummary;
import cn.wildfirechat.app.entity.TranscriptionRecord;
import cn.wildfirechat.app.exception.BizException;
import cn.wildfirechat.app.filter.AuthFilter;
import cn.wildfirechat.app.repository.ConferenceParticipantRepository;
import cn.wildfirechat.app.repository.MeetingSummaryRepository;
import cn.wildfirechat.app.repository.TranscriptionRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/records")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class RecordController {

    @Autowired
    private TranscriptionRecordRepository transcriptionRecordRepository;

    @Autowired
    private ConferenceParticipantRepository conferenceParticipantRepository;

    @Autowired
    private MeetingSummaryRepository meetingSummaryRepository;

    private void checkParticipantPermission(String conferenceId, String userId) {
        if (!conferenceParticipantRepository.findByConferenceIdAndUserId(conferenceId, userId).isPresent()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "您不是该会议的参会人员，无权查询");
        }
    }

    /**
     * 按会议ID获取转写记录
     */
    @PostMapping("/transcriptions/{conferenceId}")
    public Result<List<TranscriptionRecord>> getTranscriptionsByConferenceId(@PathVariable String conferenceId,
                                                                              HttpServletRequest httpRequest) {
        String userId = (String) httpRequest.getAttribute(AuthFilter.USER_ID_KEY);
        checkParticipantPermission(conferenceId, userId);
        List<TranscriptionRecord> records = transcriptionRecordRepository.findByConferenceIdOrderByCreatedAtDesc(conferenceId);
        return Result.success(records);
    }

    /**
     * 按会议ID和userId获取某个用户的转写记录
     */
    @PostMapping("/transcriptions/{conferenceId}/{targetUserId}")
    public Result<List<TranscriptionRecord>> getTranscriptionsByConferenceIdAndUserId(@PathVariable String conferenceId,
                                                                                       @PathVariable String targetUserId,
                                                                                       HttpServletRequest httpRequest) {
        String userId = (String) httpRequest.getAttribute(AuthFilter.USER_ID_KEY);
        checkParticipantPermission(conferenceId, userId);
        List<TranscriptionRecord> records = transcriptionRecordRepository.findByConferenceIdAndUserIdOrderByCreatedAtDesc(conferenceId, targetUserId);
        return Result.success(records);
    }

    /**
     * 按会议ID获取参与者
     */
    @PostMapping("/participants/{conferenceId}")
    public Result<List<ConferenceParticipant>> getParticipantsByConferenceId(@PathVariable String conferenceId,
                                                                              HttpServletRequest httpRequest) {
        String userId = (String) httpRequest.getAttribute(AuthFilter.USER_ID_KEY);
        checkParticipantPermission(conferenceId, userId);
        List<ConferenceParticipant> participants = conferenceParticipantRepository.findByConferenceIdOrderByCreatedAtDesc(conferenceId);
        return Result.success(participants);
    }

    /**
     * 按会议ID获取会议纪要
     */
    @PostMapping("/summary/{conferenceId}")
    public Result<MeetingSummary> getSummaryByConferenceId(@PathVariable String conferenceId,
                                                            HttpServletRequest httpRequest) {
        String userId = (String) httpRequest.getAttribute(AuthFilter.USER_ID_KEY);
        checkParticipantPermission(conferenceId, userId);
        MeetingSummary summary = meetingSummaryRepository.findByConferenceId(conferenceId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "会议纪要不存在"));
        return Result.success(summary);
    }

    /**
     * 查询当前用户参与的会议记录
     */
    @PostMapping("/my/conferences")
    public Result<List<String>> getMyConferences(HttpServletRequest httpRequest) {
        String userId = (String) httpRequest.getAttribute(AuthFilter.USER_ID_KEY);
        List<String> conferenceIds = conferenceParticipantRepository.findDistinctConferenceIdsByUserId(userId);
        return Result.success(conferenceIds);
    }
}
