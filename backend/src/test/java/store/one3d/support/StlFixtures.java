package store.one3d.support;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Small model files for tests. Geometry values do not matter; only the file layout is checked. */
public final class StlFixtures {

    private StlFixtures() {
    }

    /** A binary STL: 80-byte header, little-endian triangle count, 50 bytes per triangle. */
    public static byte[] binary(int triangles, String header) {
        ByteBuffer buffer = ByteBuffer.allocate(84 + 50 * triangles).order(ByteOrder.LITTLE_ENDIAN);
        byte[] headerBytes = header.getBytes(StandardCharsets.US_ASCII);
        buffer.put(headerBytes, 0, Math.min(80, headerBytes.length));
        buffer.putInt(80, triangles);
        for (int i = 0; i < triangles; i++) {
            buffer.putFloat(84 + 50 * i + 12, i); // first vertex x, so triangles differ
        }
        return buffer.array();
    }

    public static byte[] binary(int triangles) {
        return binary(triangles, "binary test model");
    }

    public static byte[] ascii() {
        return """
                solid cube
                  facet normal 0 0 1
                    outer loop
                      vertex 0 0 0
                      vertex 1 0 0
                      vertex 0 1 0
                    endloop
                  endfacet
                endsolid cube
                """.getBytes(StandardCharsets.US_ASCII);
    }

    /** Text that starts like an STL but is a web page. It must never pass or be served as HTML. */
    public static byte[] htmlPretendingToBeStl() {
        return "solid x\n<html><script>document.title='pwned'</script></html>\n".getBytes(StandardCharsets.US_ASCII);
    }

    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
