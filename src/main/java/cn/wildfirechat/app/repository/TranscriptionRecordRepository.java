package cn.wildfirechat.app.repository;

import cn.wildfirechat.app.entity.TranscriptionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TranscriptionRecordRepository extends JpaRepository<TranscriptionRecord, Long> {

    List<TranscriptionRecord> findByConferenceIdOrderByCreatedAtDesc(String conferenceId);

    List<TranscriptionRecord> findByConferenceIdAndUserIdOrderByCreatedAtDesc(String conferenceId, String userId);

    List<TranscriptionRecord> findByConferenceIdOrderByCreatedAtAsc(String conferenceId);
}
