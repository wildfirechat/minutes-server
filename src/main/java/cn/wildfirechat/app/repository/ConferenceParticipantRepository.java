package cn.wildfirechat.app.repository;

import cn.wildfirechat.app.entity.ConferenceParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConferenceParticipantRepository extends JpaRepository<ConferenceParticipant, Long> {

    List<ConferenceParticipant> findByConferenceIdOrderByCreatedAtDesc(String conferenceId);

    Optional<ConferenceParticipant> findByConferenceIdAndUserId(String conferenceId, String userId);

    @Query("SELECT DISTINCT cp.conferenceId FROM ConferenceParticipant cp WHERE cp.userId = ?1")
    List<String> findDistinctConferenceIdsByUserId(String userId);
}
