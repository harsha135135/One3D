package store.one3d.files;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface ArtifactRepository extends JpaRepository<Artifact, UUID> {

    /** The file only if this person uploaded it; anyone else's file is "not found". */
    Optional<Artifact> findByIdAndOwnerId(UUID id, UUID ownerId);

    /** Locks the row ({@code SELECT … FOR UPDATE}) so two finalizes or verifications cannot both apply. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Artifact a where a.id = :id")
    Optional<Artifact> findForUpdate(UUID id);
}
