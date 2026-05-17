package cn.wildfirechat.app.dto.web;

import cn.wildfirechat.app.entity.ConferenceParticipant;

import java.util.List;
import java.util.Map;

public class MeetingDetailDTO {
    private String conferenceId;
    private String summary;
    private List<ConferenceParticipant> participants;
    private List<TranscriptionDTO> transcriptions;
    private Map<String, String> userDisplayNameMap;

    public String getConferenceId() {
        return conferenceId;
    }

    public void setConferenceId(String conferenceId) {
        this.conferenceId = conferenceId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<ConferenceParticipant> getParticipants() {
        return participants;
    }

    public void setParticipants(List<ConferenceParticipant> participants) {
        this.participants = participants;
    }

    public List<TranscriptionDTO> getTranscriptions() {
        return transcriptions;
    }

    public void setTranscriptions(List<TranscriptionDTO> transcriptions) {
        this.transcriptions = transcriptions;
    }

    public Map<String, String> getUserDisplayNameMap() {
        return userDisplayNameMap;
    }

    public void setUserDisplayNameMap(Map<String, String> userDisplayNameMap) {
        this.userDisplayNameMap = userDisplayNameMap;
    }
}
