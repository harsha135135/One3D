package store.one3d.files;

import store.one3d.common.NotFoundException;

import java.util.UUID;

/** No such file, or it belongs to someone else (which is not revealed). */
public class ArtifactNotFoundException extends NotFoundException {

    public ArtifactNotFoundException(UUID artifactId) {
        super("FILE_NOT_FOUND", "File " + artifactId + " was not found.");
    }
}
