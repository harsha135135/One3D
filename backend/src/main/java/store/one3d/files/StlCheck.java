package store.one3d.files;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Reads a file once, computing its SHA-256 and checking the basic structure of
 * an STL model. It never interprets, renders or executes the content.
 *
 * <ul>
 *   <li>Binary STL: an 80-byte header, an unsigned little-endian triangle count
 *       at offset 80, then exactly 50 bytes per triangle. Many exporters also
 *       write "solid" into the header, so the size formula is checked first.</li>
 *   <li>ASCII STL: text that starts with "solid", has at least one
 *       "facet normal" and ends with "endsolid".</li>
 * </ul>
 */
final class StlCheck {

    /** {@code problem} is empty when the file passed. */
    record Result(String sha256, long sizeBytes, Optional<String> problem) {

        boolean passed() {
            return problem.isEmpty();
        }
    }

    private static final int BINARY_HEADER = 84;
    private static final int BYTES_PER_TRIANGLE = 50;
    private static final String FACET = "facet normal";
    private static final int TAIL = 1024;

    private StlCheck() {
    }

    /** Reads at most {@code maxBytes + 1} bytes; a longer file fails without being read further. */
    static Result inspect(InputStream in, long maxBytes) throws IOException {
        MessageDigest sha = sha256();
        byte[] head = new byte[BINARY_HEADER];
        int headLength = 0;
        byte[] tail = new byte[TAIL];
        long total = 0;
        boolean text = true;
        boolean sawFacet = false;
        String carry = "";
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                return new Result(hex(sha), total, Optional.of("The file is larger than " + maxBytes + " bytes."));
            }
            sha.update(buffer, 0, read);
            if (headLength < BINARY_HEADER) {
                int copy = Math.min(read, BINARY_HEADER - headLength);
                System.arraycopy(buffer, 0, head, headLength, copy);
                headLength += copy;
            }
            for (int i = 0; i < read && text; i++) {
                text = isTextByte(buffer[i]);
            }
            if (text && !sawFacet) {
                String chunk = carry + new String(buffer, 0, read, StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
                sawFacet = chunk.contains(FACET);
                carry = chunk.substring(Math.max(0, chunk.length() - FACET.length()));
            }
            keepTail(tail, buffer, read, total);
        }
        return new Result(hex(sha), total, problem(head, headLength, tail, total, text, sawFacet));
    }

    private static Optional<String> problem(byte[] head, int headLength, byte[] tail, long total, boolean text,
                                            boolean sawFacet) {
        if (total == 0) {
            return Optional.of("The file is empty.");
        }
        if (headLength == BINARY_HEADER) {
            long triangles = Integer.toUnsignedLong(
                    (head[80] & 0xff) | (head[81] & 0xff) << 8 | (head[82] & 0xff) << 16 | (head[83] & 0xff) << 24);
            if (total == BINARY_HEADER + BYTES_PER_TRIANGLE * triangles) {
                return triangles == 0 ? Optional.of("The model contains no triangles.") : Optional.empty();
            }
        }
        String start = new String(head, 0, headLength, StandardCharsets.ISO_8859_1).stripLeading().toLowerCase(Locale.ROOT);
        String end = new String(tail, 0, (int) Math.min(total, TAIL), StandardCharsets.ISO_8859_1).strip()
                .toLowerCase(Locale.ROOT);
        if (text && start.startsWith("solid") && sawFacet && end.contains("endsolid")) {
            return Optional.empty();
        }
        return Optional.of("Not a valid STL file: neither the binary layout nor the ASCII structure matches.");
    }

    /** Printable ASCII and ordinary whitespace; anything else means the file is not ASCII STL. */
    private static boolean isTextByte(byte b) {
        int c = b & 0xff;
        return c == '\t' || c == '\n' || c == '\r' || (c >= 0x20 && c < 0x7f);
    }

    /** Keeps the last {@value #TAIL} bytes seen, in order. */
    private static void keepTail(byte[] tail, byte[] buffer, int read, long totalSoFar) {
        if (read >= TAIL) {
            System.arraycopy(buffer, read - TAIL, tail, 0, TAIL);
            return;
        }
        int kept = (int) Math.min(TAIL, totalSoFar - read);
        int keep = Math.min(kept, TAIL - read);
        System.arraycopy(tail, kept - keep, tail, 0, keep);
        System.arraycopy(buffer, 0, tail, keep, read);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", e);
        }
    }

    private static String hex(MessageDigest sha) {
        return HexFormat.of().formatHex(sha.digest());
    }
}
