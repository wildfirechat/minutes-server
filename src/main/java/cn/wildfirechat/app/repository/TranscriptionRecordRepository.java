package cn.wildfirechat.app.repository;

import cn.wildfirechat.app.entity.TranscriptionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TranscriptionRecordRepository extends JpaRepository<TranscriptionRecord, Long> {

    List<TranscriptionRecord> findByConferenceIdOrderByCreatedAtDesc(String conferenceId);

    List<TranscriptionRecord> findByConferenceIdAndUserIdOrderByCreatedAtDesc(String conferenceId, String userId);

    List<TranscriptionRecord> findByConferenceIdOrderByCreatedAtAsc(String conferenceId);

    @Query("SELECT t FROM TranscriptionRecord t WHERE t.conferenceId = ?1 ORDER BY t.createdAt ASC")
    Page<TranscriptionRecord> findPageByConferenceIdOrderByCreatedAtAsc(String conferenceId, Pageable pageable);
}
