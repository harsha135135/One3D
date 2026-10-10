package store.one3d.files;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Asks to upload one STL file. The size is the customer's claim: it is signed
 * into the upload URL, compared with what was stored, and checked again when
 * the file is read.
 */
record StartUploadRequest(
        @NotBlank @Size(max = FileNames.MAX_LENGTH) String filename,
        @NotNull @Positive Long sizeBytes) {
}
