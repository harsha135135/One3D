package store.one3d.requests;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SubmissionRevisionRepository extends JpaRepository<SubmissionRevision, UUID> {

    List<SubmissionRevision> findByRequestIdOrderByRevisionNumberAsc(UUID requestId);

    Optional<SubmissionRevision> findFirstByRequestIdOrderByRevisionNumberDesc(UUID requestId);

    Optional<SubmissionRevision> findByRequestIdAndRevisionNumber(UUID requestId, int revisionNumber);

    boolean existsByArtifactId(UUID artifactId);

    /** The latest revision of each request, for list pages. */
    @Query("""
            select r from SubmissionRevision r
            where r.requestId in :requestIds
              and r.revisionNumber = (select max(o.revisionNumber) from SubmissionRevision o where o.requestId = r.requestId)
            """)
    List<SubmissionRevision> findLatestOf(Collection<UUID> requestIds);
}
