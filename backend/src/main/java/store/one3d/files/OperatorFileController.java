package store.one3d.files;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Operator actions on files. Operators download a file through the request it
 * was submitted with ({@code /api/ops/requests/...}), not by file id.
 */
@RestController
@RequestMapping("/api/ops/files")
class OperatorFileController {

    private final FileService files;

    OperatorFileController(FileService files) {
        this.files = files;
    }

    /** Tries the check again after its automatic tries ran out (storage was unreachable). */
    @PostMapping("/{artifactId}/retry-verification")
    ArtifactResponse retryVerification(@PathVariable UUID artifactId) {
        return files.retryVerification(artifactId);
    }
}
