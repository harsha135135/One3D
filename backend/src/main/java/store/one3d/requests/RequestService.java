package store.one3d.requests;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import store.one3d.common.ConflictException;
import store.one3d.common.NotFoundException;
import store.one3d.common.PageResponse;
import store.one3d.common.Timestamps;
import store.one3d.files.ArtifactResponse;
import store.one3d.files.FileService;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Custom requests: customers submit revisions, operators review them, and the
 * quotes module prices and accepts them through the small API at the end.
 * Every change locks the request row first.
 */
@Service
public class RequestService {

    private final CustomRequestRepository requests;
    private final SubmissionRevisionRepository revisions;
    private final FileService files;
    private final ApplicationEventPublisher events;

    RequestService(CustomRequestRepository requests, SubmissionRevisionRepository revisions, FileService files,
                   ApplicationEventPublisher events) {
        this.requests = requests;
        this.revisions = revisions;
        this.files = files;
        this.events = events;
    }

    // --- customers -----------------------------------------------------------

    /** A new request with revision 1. The file must be the customer's own and checked. */
    @Transactional
    public RequestDetail submit(UUID customerId, Requirements requirements) {
        requireUnusedCheckedFile(customerId, requirements.artifactId());
        Instant now = Timestamps.now();
        CustomRequest request = requests.saveAndFlush(CustomRequest.submit(customerId, now));
        revisions.save(new SubmissionRevision(request.getId(), 1, requirements, now));
        return detail(request);
    }

    /**
     * A new revision of the customer's request: a new file and requirements,
     * reviewed from scratch. An open quote is withdrawn in the same
     * transaction ({@link RevisionSubmitted}).
     */
    @Transactional
    public RequestDetail revise(UUID customerId, UUID requestId, Requirements requirements) {
        CustomRequest request = lockOwned(customerId, requestId);
        request.acceptNewRevision(Timestamps.now());
        requireUnusedCheckedFile(customerId, requirements.artifactId());
        int next = revisions.findFirstByRequestIdOrderByRevisionNumberDesc(requestId)
                .map(SubmissionRevision::getRevisionNumber).orElse(0) + 1;
        SubmissionRevision revision = revisions.save(new SubmissionRevision(requestId, next, requirements, Timestamps.now()));
        events.publishEvent(new RevisionSubmitted(requestId, revision.getId(), next));
        return detail(request);
    }

    @Transactional(readOnly = true)
    public RequestDetail getOwn(UUID customerId, UUID requestId) {
        return detail(findOwned(customerId, requestId));
    }

    @Transactional(readOnly = true)
    public PageResponse<RequestSummary> listOwn(UUID customerId, int page, int size) {
        return summaries(requests.findByCustomerIdOrderByCreatedAtDesc(customerId,
                PageRequest.of(PageResponse.page(page), PageResponse.size(size))));
    }

    /** For the quotes module: the request must belong to this customer. */
    @Transactional(readOnly = true)
    public void requireOwned(UUID customerId, UUID requestId) {
        findOwned(customerId, requestId);
    }

    // --- operators -----------------------------------------------------------

    /** Requests in the given states, longest waiting first. */
    @Transactional(readOnly = true)
    public PageResponse<RequestSummary> list(Collection<RequestStatus> statuses, int page, int size) {
        return summaries(requests.findByStatusInOrderByUpdatedAtAsc(statuses,
                PageRequest.of(PageResponse.page(page), PageResponse.size(size))));
    }

    @Transactional(readOnly = true)
    public RequestDetail get(UUID requestId) {
        return detail(find(requestId));
    }

    @Transactional
    public RequestDetail requestChanges(UUID requestId, String message) {
        CustomRequest request = lock(requestId);
        request.requestChanges(message, Timestamps.now());
        return detail(request);
    }

    @Transactional
    public RequestDetail reject(UUID requestId, String reason) {
        CustomRequest request = lock(requestId);
        request.reject(reason, Timestamps.now());
        return detail(request);
    }

    /** A short-lived download link for the file of one revision of this request. */
    public URI revisionFile(UUID requestId, int revisionNumber) {
        UUID artifactId = revisions.findByRequestIdAndRevisionNumber(requestId, revisionNumber)
                .map(SubmissionRevision::getArtifactId)
                .orElseThrow(() -> new NotFoundException("REVISION_NOT_FOUND",
                        "Request " + requestId + " has no revision " + revisionNumber + "."));
        return files.downloadForReview(artifactId);
    }

    // --- for the quotes module (each joins the caller's transaction) ----------

    /**
     * Locks the request and checks that it can be quoted and that the revision
     * is its latest. A quote always prices the content currently under review.
     */
    @Transactional
    public QuotableRevision lockForQuote(UUID requestId, UUID revisionId) {
        CustomRequest request = lock(requestId);
        request.requireQuotable();
        return latest(request, revisionId);
    }

    /** Locks the customer's request; anyone else's request is "not found". */
    @Transactional
    public void lockForAcceptance(UUID customerId, UUID requestId) {
        lockOwned(customerId, requestId);
    }

    /**
     * After {@link #lockForAcceptance}: the request must still be QUOTED and
     * the quote's revision must still be the latest one.
     */
    @Transactional
    public QuotableRevision requireAwaitingAcceptance(UUID requestId, UUID revisionId) {
        CustomRequest request = lock(requestId);
        if (request.getStatus() != RequestStatus.QUOTED) {
            throw new ConflictException("QUOTE_NOT_CURRENT",
                    "Request " + requestId + " is " + request.getStatus() + "; this quote can no longer be accepted.");
        }
        return latest(request, revisionId);
    }

    @Transactional
    public void markQuoted(UUID requestId) {
        lock(requestId).markQuoted(Timestamps.now());
    }

    @Transactional
    public void markOrdered(UUID requestId) {
        lock(requestId).markOrdered(Timestamps.now());
    }

    // --- helpers -------------------------------------------------------------

    private QuotableRevision latest(CustomRequest request, UUID revisionId) {
        SubmissionRevision latest = revisions.findFirstByRequestIdOrderByRevisionNumberDesc(request.getId()).orElseThrow();
        if (!latest.getId().equals(revisionId)) {
            throw new ConflictException("REVISION_NOT_LATEST", "Revision " + revisionId
                    + " is not the latest revision of request " + request.getId() + ".");
        }
        ArtifactResponse file = files.describe(List.of(latest.getArtifactId())).get(latest.getArtifactId());
        return new QuotableRevision(request.getId(), request.getCustomerId(), latest.getId(),
                latest.getRevisionNumber(), latest.getMaterial(), latest.getColour(), file.id(), file.filename(),
                file.versionId(), file.sha256());
    }

    private void requireUnusedCheckedFile(UUID customerId, UUID artifactId) {
        files.requireSubmittable(customerId, artifactId);
        if (revisions.existsByArtifactId(artifactId)) {
            throw new ConflictException("FILE_ALREADY_SUBMITTED",
                    "File " + artifactId + " already belongs to a submission; upload the model again for a new one.");
        }
    }

    private RequestDetail detail(CustomRequest request) {
        List<SubmissionRevision> all = revisions.findByRequestIdOrderByRevisionNumberAsc(request.getId());
        Map<UUID, ArtifactResponse> filesById = files.describe(all.stream().map(SubmissionRevision::getArtifactId).toList());
        return new RequestDetail(request.getId(), request.getCustomerId(), request.getStatus(),
                request.getOperatorMessage(), request.getCreatedAt(), request.getUpdatedAt(),
                all.stream().map(revision -> new RequestDetail.Revision(revision.getId(), revision.getRevisionNumber(),
                        revision.getMaterial(), revision.getColour(), revision.getNotes(), revision.getCreatedAt(),
                        filesById.get(revision.getArtifactId()))).toList());
    }

    private PageResponse<RequestSummary> summaries(Page<CustomRequest> page) {
        List<UUID> ids = page.getContent().stream().map(CustomRequest::getId).toList();
        Map<UUID, SubmissionRevision> latest = ids.isEmpty() ? Map.of() : revisions.findLatestOf(ids).stream()
                .collect(Collectors.toMap(SubmissionRevision::getRequestId, Function.identity()));
        Map<UUID, ArtifactResponse> filesById = files.describe(
                latest.values().stream().map(SubmissionRevision::getArtifactId).toList());
        return PageResponse.of(page, request -> {
            SubmissionRevision revision = latest.get(request.getId());
            return new RequestSummary(request.getId(), request.getStatus(), revision.getRevisionNumber(),
                    filesById.get(revision.getArtifactId()).filename(), revision.getMaterial(), revision.getColour(),
                    request.getCreatedAt(), request.getUpdatedAt());
        });
    }

    private CustomRequest lockOwned(UUID customerId, UUID requestId) {
        CustomRequest request = lock(requestId);
        if (!request.getCustomerId().equals(customerId)) {
            throw new RequestNotFoundException(requestId);
        }
        return request;
    }

    private CustomRequest lock(UUID requestId) {
        return requests.findForUpdate(requestId).orElseThrow(() -> new RequestNotFoundException(requestId));
    }

    private CustomRequest find(UUID requestId) {
        return requests.findById(requestId).orElseThrow(() -> new RequestNotFoundException(requestId));
    }

    private CustomRequest findOwned(UUID customerId, UUID requestId) {
        return requests.findByIdAndCustomerId(requestId, customerId)
                .orElseThrow(() -> new RequestNotFoundException(requestId));
    }
}
