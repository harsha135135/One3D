package store.one3d.files;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;

/** The response for a download: a 302 to a signed URL that is not cached or sent on as a referrer. */
public final class DownloadRedirect {

    private DownloadRedirect() {
    }

    public static ResponseEntity<Void> to(URI signedUrl) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(signedUrl)
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .build();
    }
}
