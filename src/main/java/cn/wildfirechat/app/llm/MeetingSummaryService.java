package cn.wildfirechat.app.llm;

import cn.wildfirechat.app.entity.ConferenceParticipant;
import cn.wildfirechat.app.entity.MeetingSummary;
import cn.wildfirechat.app.entity.TranscriptionRecord;
import cn.wildfirechat.app.repository.ConferenceParticipantRepository;
import cn.wildfirechat.app.repository.MeetingSummaryRepository;
import cn.wildfirechat.app.repository.TranscriptionRecordRepository;
import cn.wildfirechat.common.ErrorCode;
import cn.wildfirechat.pojos.Conversation;
import cn.wildfirechat.pojos.InputOutputUserInfo;
import cn.wildfirechat.pojos.MessagePayload;
import cn.wildfirechat.sdk.RobotService;
import cn.wildfirechat.sdk.UserAdmin;
import cn.wildfirechat.sdk.model.IMResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class MeetingSummaryService {
    private static final Logger LOG = LoggerFactory.getLogger(MeetingSummaryService.class);
    private static final Pattern CORRECTED_LINE_PATTERN = Pattern.compile("^(\\d+)\\.\\s*\\[(.*?)\\]\\s*(.*)$");
    private static final int BATCH_SIZE = 300;
    private static final long USER_CACHE_EXPIRE_MS = 5 * 60 * 1000;

    private static class UserCacheEntry {
        final String displayName;
        final long expireTime;
        UserCacheEntry(String displayName, long expireTime) {
            this.displayName = displayName;
            this.expireTime = expireTime;
        }
    }

    private final Map<String, UserCacheEntry> userDisplayNameCache = new ConcurrentHashMap<>();

    @Autowired
    private TranscriptionRecordRepository transcriptionRecordRepository;

    @Autowired
    private ConferenceParticipantRepository conferenceParticipantRepository;

    @Autowired
    private LLMService llmService;

    @Autowired
    private MeetingSummaryRepository meetingSummaryRepository;

    @Async("asyncExecutor")
    public void generateAndSendSummary(String conferenceId, RobotService robotService, String robotId) {
        LOG.info("Start generating meeting summary for conferenceId={}", conferenceId);

        try {
            Thread.sleep(3000);

            List<TranscriptionRecord> records = transcriptionRecordRepository.findByConferenceIdOrderByCreatedAtAsc(conferenceId);
            if (records == null || records.isEmpty()) {
                LOG.info("No transcription records found for conferenceId={}, skip summary", conferenceId);
                return;
            }

            correctTranscriptions(records, conferenceId);

            String summary = generateSummary(records, conferenceId);
            if (summary == null || summary.isEmpty()) {
                LOG.warn("LLM returned empty summary for conferenceId={}", conferenceId);
                return;
            }

            try {
                MeetingSummary meetingSummary = new MeetingSummary();
                meetingSummary.setConferenceId(conferenceId);
                meetingSummary.setSummary(summary);
                meetingSummaryRepository.save(meetingSummary);
                LOG.info("Saved meeting summary to database for conferenceId={}", conferenceId);
            } catch (Exception e) {
                LOG.error("Failed to save meeting summary to database for conferenceId={}", conferenceId, e);
            }

            List<ConferenceParticipant> participants = conferenceParticipantRepository.findByConferenceIdOrderByCreatedAtDesc(conferenceId);
            if (participants == null || participants.isEmpty()) {
                LOG.warn("No participants found for conferenceId={}", conferenceId);
                return;
            }

            List<String> participantUserIds = participants.stream()
                    .map(ConferenceParticipant::getUserId)
                    .distinct()
                    .collect(Collectors.toList());

            for (String userId : participantUserIds) {
                try {
                    Conversation conversation = new Conversation(0, userId, 0);
                    MessagePayload payload = new MessagePayload();
                    payload.setType(1);
                    payload.setSearchableContent("【会议纪要】\n\n" + summary);
                    IMResult<cn.wildfirechat.pojos.SendMessageResult> result = robotService.sendMessage(robotId, conversation, payload);
                    if (result != null && result.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                        LOG.info("Sent meeting summary to userId={} for conferenceId={}", userId, conferenceId);
                    } else {
                        LOG.error("Failed to send meeting summary to userId={} for conferenceId={}, code={}", userId, conferenceId, result != null ? result.getCode() : "null");
                    }
                } catch (Exception e) {
                    LOG.error("Failed to send meeting summary to userId={} for conferenceId={}", userId, conferenceId, e);
                }
            }

            LOG.info("Meeting summary process completed for conferenceId={}", conferenceId);
        } catch (Exception e) {
            LOG.error("Failed to generate meeting summary for conferenceId={}", conferenceId, e);
        }
    }

    private String generateSummary(List<TranscriptionRecord> records, String conferenceId) {
        String systemPrompt = "你是一个专业的会议记录整理助手。请根据提供的会议录音转写文本，整理成一份结构化的会议纪要。要求包括：\n" +
                "1. 会议要点总结\n" +
                "2. 待办事项（如果有）\n" +
                "3. 关键决策（如果有）\n" +
                "请使用中文输出，保持简洁明了，避免重复，要根据内容生成，不要推测和演绎。";
        String userPrompt = "请整理以下会议纪要：\n\n" + buildTranscript(records);

        if (!llmService.isExceedContext(systemPrompt, userPrompt)) {
            return llmService.chat(systemPrompt, userPrompt);
        }

        LOG.info("Transcript too long for conferenceId={}, switch to batch summary mode", conferenceId);
        List<String> partialSummaries = new ArrayList<>();

        for (int i = 0; i < records.size(); i += BATCH_SIZE) {
            List<TranscriptionRecord> batch = records.subList(i, Math.min(i + BATCH_SIZE, records.size()));
            String batchSystem = "你是一个专业的会议记录整理助手。请对以下会议片段整理出要点、待办事项和关键决策。\n" +
                    "请使用中文输出，保持简洁明了，避免重复，要根据内容生成，不要推测和演绎。";
            String batchUser = "请整理以下会议片段：\n\n" + buildTranscript(batch);
            String batchSummary = llmService.chat(batchSystem, batchUser);
            if (batchSummary != null && !batchSummary.isEmpty()) {
                partialSummaries.add(batchSummary);
            }
        }

        if (partialSummaries.isEmpty()) {
            return null;
        }

        StringBuilder summaryBuilder = new StringBuilder();
        for (int i = 0; i < partialSummaries.size(); i++) {
            summaryBuilder.append("--- 第 ").append(i + 1).append(" 段小结 ---\n");
            summaryBuilder.append(partialSummaries.get(i)).append("\n\n");
        }

        String finalSystem = "你是一个专业的会议记录整理助手。请将以下多段会议小结汇总成一份完整的结构化会议纪要，包含：\n" +
                "1. 会议要点总结\n" +
                "2. 待办事项（如果有）\n" +
                "3. 关键决策（如果有）\n" +
                "请使用中文输出，保持简洁明了，避免重复，要根据内容生成，不要推测和演绎。";
        String finalUser = "请将以下小结汇总成完整纪要：\n\n" + summaryBuilder.toString();
        return llmService.chat(finalSystem, finalUser);
    }

    private void correctTranscriptions(List<TranscriptionRecord> records, String conferenceId) {
        if (records == null || records.isEmpty()) {
            return;
        }

        String systemPrompt = "你是一个专业的语音识别文本矫正助手。请对以下语音识别转写文本进行逐条矫正。主要修正：\n" +
                "1. 同音字、错别字\n" +
                "2. 专有名词（人名、公司名、技术术语等）\n" +
                "3. 标点符号和语句通顺性\n" +
                "4. 去除无意义的语气词重复\n\n" +
                "重要要求：请保持原有格式，对每一行分别矫正后按相同编号返回。不要改变编号顺序，也不要合并或拆分行。";
        String originalText = buildOriginalText(records, 0);

        if (!llmService.isExceedContext(systemPrompt, originalText)) {
            doCorrectBatch(records, 0, systemPrompt, originalText, conferenceId);
            return;
        }

        LOG.info("Transcription too long for conferenceId={}, switch to batch correction mode, records={}", conferenceId, records.size());
        for (int i = 0; i < records.size(); i += BATCH_SIZE) {
            List<TranscriptionRecord> batch = records.subList(i, Math.min(i + BATCH_SIZE, records.size()));
            String batchText = buildOriginalText(batch, i);
            doCorrectBatch(batch, i, systemPrompt, batchText, conferenceId);
        }
    }

    private void doCorrectBatch(List<TranscriptionRecord> records, int offset, String systemPrompt, String originalText, String conferenceId) {
        try {
            String userPrompt = "请矫正以下语音识别文本（保持编号格式返回）：\n\n" + originalText;
            String correctedText = llmService.chat(systemPrompt, userPrompt);
            if (correctedText == null || correctedText.isEmpty()) {
                LOG.warn("LLM returned empty correction for batch offset={}, conferenceId={}", offset, conferenceId);
                return;
            }

            int matchedCount = 0;
            String[] lines = correctedText.split("\n");
            for (String line : lines) {
                Matcher matcher = CORRECTED_LINE_PATTERN.matcher(line.trim());
                if (matcher.matches()) {
                    int globalIndex = Integer.parseInt(matcher.group(1)) - 1;
                    int localIndex = globalIndex - offset;
                    String correctedContent = matcher.group(3).trim();
                    if (localIndex >= 0 && localIndex < records.size()) {
                        records.get(localIndex).setCorrectedContent(correctedContent);
                        matchedCount++;
                    }
                }
            }

            transcriptionRecordRepository.saveAll(records);
            LOG.info("Transcription correction completed for batch offset={}, conferenceId={}, matched={}/{}",
                    offset, conferenceId, matchedCount, records.size());
        } catch (Exception e) {
            LOG.error("Failed to correct transcriptions for batch offset={}, conferenceId={}", offset, conferenceId, e);
        }
    }

    private String getUserDisplayName(String userId) {
        UserCacheEntry entry = userDisplayNameCache.get(userId);
        if (entry != null && System.currentTimeMillis() < entry.expireTime) {
            return entry.displayName;
        }
        try {
            IMResult<InputOutputUserInfo> result = UserAdmin.getUserByUserId(userId);
            if (result != null && result.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS && result.getResult() != null) {
                String displayName = result.getResult().getDisplayName();
                if (displayName == null || displayName.isEmpty()) {
                    displayName = userId;
                }
                userDisplayNameCache.put(userId, new UserCacheEntry(displayName, System.currentTimeMillis() + USER_CACHE_EXPIRE_MS));
                return displayName;
            }
        } catch (Exception e) {
            LOG.error("Failed to get user info for userId={}", userId, e);
        }
        userDisplayNameCache.put(userId, new UserCacheEntry(userId, System.currentTimeMillis() + USER_CACHE_EXPIRE_MS));
        return userId;
    }

    private String buildTranscript(List<TranscriptionRecord> records) {
        StringBuilder sb = new StringBuilder();
        for (TranscriptionRecord record : records) {
            String text = record.getCorrectedContent() != null ? record.getCorrectedContent() : record.getContent();
            sb.append("[").append(getUserDisplayName(record.getUserId())).append("] ").append(text).append("\n");
        }
        return sb.toString();
    }

    private String buildOriginalText(List<TranscriptionRecord> records, int offset) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < records.size(); i++) {
            TranscriptionRecord record = records.get(i);
            sb.append(i + 1 + offset).append(". [").append(record.getUserId()).append("] ")
                    .append(record.getContent()).append("\n");
        }
        return sb.toString();
    }
}
