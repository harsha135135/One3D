package store.one3d.files;

import org.junit.jupiter.api.Test;
import store.one3d.support.StlFixtures;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class StlCheckTest {

    private static final long LIMIT = 20_971_520;

    @Test
    void aBinaryModelPassesWithItsDigestAndSize() throws IOException {
        byte[] model = StlFixtures.binary(12);

        StlCheck.Result result = check(model);

        assertThat(result.passed()).isTrue();
        assertThat(result.sizeBytes()).isEqualTo(84 + 50 * 12);
        assertThat(result.sha256()).isEqualTo(StlFixtures.sha256(model));
    }

    @Test
    void aBinaryModelWhoseHeaderStartsWithSolidIsStillBinary() throws IOException {
        assertThat(check(StlFixtures.binary(3, "solid exported by a CAD tool")).passed()).isTrue();
    }

    @Test
    void aBinaryModelWithNoTrianglesFails() throws IOException {
        assertThat(check(StlFixtures.binary(0)).problem()).contains("The model contains no triangles.");
    }

    @Test
    void aTruncatedBinaryModelFails() throws IOException {
        byte[] model = StlFixtures.binary(5);

        assertThat(check(Arrays.copyOf(model, model.length - 1)).passed()).isFalse();
    }

    @Test
    void anAsciiModelPasses() throws IOException {
        assertThat(check(StlFixtures.ascii()).passed()).isTrue();
    }

    @Test
    void textWithoutFacetsOrWithoutEndsolidFails() throws IOException {
        assertThat(check(ascii("solid empty\nendsolid empty\n")).passed()).isFalse();
        assertThat(check(ascii("solid cut\n facet normal 0 0 1\n outer loop\n")).passed()).isFalse();
        assertThat(check(StlFixtures.htmlPretendingToBeStl()).passed()).isFalse();
        assertThat(check(new byte[0]).problem()).contains("The file is empty.");
    }

    @Test
    void aLargeAsciiModelIsCheckedAcrossReadBoundaries() throws IOException {
        StringBuilder text = new StringBuilder("solid big\n");
        String facet = " facet normal 0 0 1\n  outer loop\n   vertex 0 0 0\n   vertex 1 0 0\n   vertex 0 1 0\n"
                + "  endloop\n endfacet\n";
        while (text.length() < 300_000) {
            text.append(facet);
        }
        text.append("endsolid big\n");

        assertThat(check(ascii(text.toString())).passed()).isTrue();
    }

    @Test
    void aFileOverTheLimitFailsAfterReadingOnlyOneByteMore() throws IOException {
        CountingEndlessStream endless = new CountingEndlessStream();

        StlCheck.Result result = StlCheck.inspect(endless, 1000);

        assertThat(result.problem()).contains("The file is larger than 1000 bytes.");
        // Reads are 64 KiB at a time, so it stops within the first read after the limit.
        assertThat(endless.served).isLessThanOrEqualTo(1000 + 64 * 1024);
    }

    private static StlCheck.Result check(byte[] content) throws IOException {
        return StlCheck.inspect(new ByteArrayInputStream(content), LIMIT);
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    /** An endless file that counts how many bytes were read from it. */
    private static final class CountingEndlessStream extends InputStream {

        long served;

        @Override
        public int read() {
            served++;
            return 'x';
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            Arrays.fill(buffer, offset, offset + length, (byte) 'x');
            served += length;
            return length;
        }
    }
}
