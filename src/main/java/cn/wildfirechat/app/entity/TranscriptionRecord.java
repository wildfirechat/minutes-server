package cn.wildfirechat.app.entity;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "transcription_record")
public class TranscriptionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conference_id", nullable = false, length = 128)
    private String conferenceId;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "timestamp_ms")
    private Long timestampMs;

    @Column(name = "duration")
    private Integer duration;

    @Column(name = "content", length = 4000)
    private String content;

    @Column(name = "corrected_content", length = 4000)
    private String correctedContent;

    @Column(name = "segment_name", length = 512)
    private String segmentName;

    @Column(name = "created_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = new Date();
    }

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

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
