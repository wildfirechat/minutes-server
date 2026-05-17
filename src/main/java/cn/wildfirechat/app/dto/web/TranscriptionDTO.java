package cn.wildfirechat.app.dto.web;

import java.util.Date;

public class TranscriptionDTO {
    private Long id;
    private String conferenceId;
    private String userId;
    private Long timestampMs;
    private Integer duration;
    private String content;
    private String correctedContent;
    private String segmentName;
    private String audioUrl;
    private Long messageId;
    private Boolean screenSharing;
    private Date createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getConferenceId() {
        return conferenceId;
    }

    public void setConferenceId(String conferenceId) {
        this.conferenceId = conferenceId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Long getTimestampMs() {
        return timestampMs;
    }

    public void setTimestampMs(Long timestampMs) {
        this.timestampMs = timestampMs;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCorrectedContent() {
        return correctedContent;
    }

    public void setCorrectedContent(String correctedContent) {
        this.correctedContent = correctedContent;
    }

    public String getSegmentName() {
        return segmentName;
    }

    public void setSegmentName(String segmentName) {
        this.segmentName = segmentName;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public Boolean getScreenSharing() {
        return screenSharing;
    }

    public void setScreenSharing(Boolean screenSharing) {
        this.screenSharing = screenSharing;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
