package store.one3d.files;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import store.one3d.identity.CurrentUser;

import java.util.UUID;

/**
 * The signed-in customer's model files. The browser sends file bytes straight
 * to storage with the presigned URL from {@code POST /uploads}; this API only
 * hands out that URL, confirms the upload and issues download links.
 */
@RestController
@RequestMapping("/api/files")
class FileController {

    private final FileService files;
    private final CurrentUser currentUser;

    FileController(FileService files, CurrentUser currentUser) {
        this.files = files;
        this.currentUser = currentUser;
    }

    @PostMapping("/uploads")
    ResponseEntity<UploadResponse> startUpload(@Valid @RequestBody StartUploadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(files.startUpload(currentUser.id(), request.filename(), request.sizeBytes()));
    }

    @PostMapping("/{artifactId}/finalize")
    ArtifactResponse finalizeUpload(@PathVariable UUID artifactId) {
        return files.finalizeUpload(currentUser.id(), artifactId);
    }

    @GetMapping("/{artifactId}")
    ArtifactResponse get(@PathVariable UUID artifactId) {
        return files.getOwn(currentUser.id(), artifactId);
    }

    /** Redirects to a short-lived link for the exact checked version. */
    @GetMapping("/{artifactId}/download")
    ResponseEntity<Void> download(@PathVariable UUID artifactId) {
        return DownloadRedirect.to(files.ownDownload(currentUser.id(), artifactId));
    }
}
