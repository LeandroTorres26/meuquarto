package com.meuquarto.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meuquarto.profile.domain.AvatarException.Problem;
import com.meuquarto.support.TestImages;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** The avatar pipeline (FR-05, docs/security.md "Malicious uploads"): what is accepted, what is refused and what comes out. */
class AvatarProcessorTest {

    private final AvatarProcessor processor = new AvatarProcessor();

    private static Problem problemOf(Runnable action) {
        try {
            action.run();
        } catch (AvatarException e) {
            return e.problem();
        }
        throw new AssertionError("expected an AvatarException");
    }

    private Problem refusalOf(byte[] upload) {
        return problemOf(() -> processor.process(upload));
    }

    private static boolean isReddish(int rgb) {
        return ((rgb >> 16) & 0xFF) > 180 && ((rgb >> 8) & 0xFF) < 90 && (rgb & 0xFF) < 90;
    }

    private static boolean isBluish(int rgb) {
        return (rgb & 0xFF) > 180 && ((rgb >> 16) & 0xFF) < 90 && ((rgb >> 8) & 0xFF) < 90;
    }

    // --- output ---

    @Test
    void aJpegBecomesA512SquareJpeg() {
        AvatarProcessor.Result result = processor.process(
                TestImages.jpeg(TestImages.halves(800, 600, Color.RED, Color.BLUE)));

        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(ImageType.detect(result.content())).contains(ImageType.JPEG);
        BufferedImage out = TestImages.decode(result.content());
        assertThat(out.getWidth()).isEqualTo(512);
        assertThat(out.getHeight()).isEqualTo(512);
    }

    @Test
    void aPngBecomesA512SquareJpegAndTransparencyBecomesWhite() {
        BufferedImage transparent = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB); // all pixels transparent
        AvatarProcessor.Result result = processor.process(TestImages.png(transparent));

        BufferedImage out = TestImages.decode(result.content());
        assertThat(out.getWidth()).isEqualTo(512);
        assertThat(out.getHeight()).isEqualTo(512);
        int pixel = out.getRGB(256, 256);
        assertThat(pixel & 0xFFFFFF).isGreaterThan(0xF0F0F0);
    }

    @Test
    void aWidePictureIsCroppedToACenteredSquare() {
        // 1000x500: the center square is x 250..750, so it holds half red and half blue.
        AvatarProcessor.Result result = processor.process(
                TestImages.png(TestImages.halves(1000, 500, Color.RED, Color.BLUE)));

        BufferedImage out = TestImages.decode(result.content());
        assertThat(isReddish(out.getRGB(60, 256))).isTrue();
        assertThat(isBluish(out.getRGB(450, 256))).isTrue();
    }

    @Test
    void aSmallPictureIsScaledUp() {
        AvatarProcessor.Result result = processor.process(TestImages.png(TestImages.halves(64, 64, Color.RED, Color.BLUE)));
        assertThat(TestImages.decode(result.content()).getWidth()).isEqualTo(512);
    }

    @Test
    void aBigPictureWithinTheLimitIsDownscaledWithoutTrouble() {
        AvatarProcessor.Result result = processor.process(
                TestImages.jpeg(TestImages.halves(4096, 3000, Color.RED, Color.BLUE)));
        BufferedImage out = TestImages.decode(result.content());
        assertThat(out.getWidth()).isEqualTo(512);
        assertThat(out.getHeight()).isEqualTo(512);
    }

    // --- metadata ---

    @Test
    void exifAndCommentsAreGoneAfterReEncoding() {
        byte[] jpeg = TestImages.jpeg(TestImages.halves(200, 200, Color.RED, Color.BLUE));
        jpeg = TestImages.withJpegSegment(jpeg, 0xE1, TestImages.exifPayload(1, "GPS-LAT-23.55-SECRET"));
        jpeg = TestImages.withJpegSegment(jpeg, 0xFE, "camera-owner-note".getBytes(StandardCharsets.US_ASCII));
        assertThat(TestImages.contains(jpeg, "GPS-LAT-23.55-SECRET")).isTrue(); // the input does carry it

        byte[] out = processor.process(jpeg).content();

        assertThat(TestImages.contains(out, "GPS-LAT")).isFalse();
        assertThat(TestImages.contains(out, "Exif")).isFalse();
        assertThat(TestImages.contains(out, "camera-owner-note")).isFalse();
    }

    @Test
    void thePhotoIsTurnedUprightByItsExifOrientation() {
        // Stored with the top red and the bottom blue, and tagged "rotate 90 clockwise": the top edge becomes the right edge.
        byte[] jpeg = TestImages.jpeg(TestImages.stacked(200, 200, Color.RED, Color.BLUE));
        jpeg = TestImages.withJpegSegment(jpeg, 0xE1, TestImages.exifPayload(6, "x"));

        BufferedImage out = TestImages.decode(processor.process(jpeg).content());

        assertThat(isBluish(out.getRGB(60, 256))).isTrue();
        assertThat(isReddish(out.getRGB(450, 256))).isTrue();
    }

    @Test
    void orientationEightTurnsTheOtherWay() {
        byte[] jpeg = TestImages.jpeg(TestImages.stacked(200, 200, Color.RED, Color.BLUE));
        jpeg = TestImages.withJpegSegment(jpeg, 0xE1, TestImages.exifPayload(8, "x"));

        BufferedImage out = TestImages.decode(processor.process(jpeg).content());

        assertThat(isReddish(out.getRGB(60, 256))).isTrue();
        assertThat(isBluish(out.getRGB(450, 256))).isTrue();
    }

    @Test
    void aMalformedExifBlockIsIgnoredNotTrusted() {
        byte[] jpeg = TestImages.jpeg(TestImages.stacked(200, 200, Color.RED, Color.BLUE));
        jpeg = TestImages.withJpegSegment(jpeg, 0xE1, "Exif\0\0MM\0*\377\377\377\377".getBytes(StandardCharsets.ISO_8859_1));

        BufferedImage out = TestImages.decode(processor.process(jpeg).content());

        assertThat(isReddish(out.getRGB(256, 60))).isTrue(); // untouched orientation: red stays on top
    }

    // --- refusals ---

    @Test
    void emptyAndOversizedUploadsAreRefused() {
        assertThat(refusalOf(new byte[0])).isEqualTo(Problem.AVATAR_EMPTY);
        assertThat(refusalOf(null)).isEqualTo(Problem.AVATAR_EMPTY);
        byte[] big = new byte[AvatarProcessor.MAX_BYTES + 1];
        big[0] = (byte) 0xFF;
        big[1] = (byte) 0xD8;
        big[2] = (byte) 0xFF;
        assertThat(refusalOf(big)).isEqualTo(Problem.AVATAR_TOO_LARGE);
    }

    @Test
    void theTypeComesFromTheBytesNotFromTheName() {
        assertThat(refusalOf("<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(Problem.AVATAR_UNSUPPORTED_TYPE);
        assertThat(refusalOf("<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8))).isEqualTo(Problem.AVATAR_UNSUPPORTED_TYPE);
        assertThat(refusalOf("GIF89a\u0001\u0000\u0001\u0000".getBytes(StandardCharsets.ISO_8859_1)))
                .isEqualTo(Problem.AVATAR_UNSUPPORTED_TYPE);
        assertThat(refusalOf("%PDF-1.7 fake".getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo(Problem.AVATAR_UNSUPPORTED_TYPE);
        assertThat(refusalOf("MZ\u0090\u0000".getBytes(StandardCharsets.ISO_8859_1)))
                .isEqualTo(Problem.AVATAR_UNSUPPORTED_TYPE);
    }

    @Test
    void webpIsRefusedBecauseTheJdkCannotDecodeIt() {
        byte[] webp = "RIFF$\u0000\u0000\u0000WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);
        assertThat(refusalOf(webp)).isEqualTo(Problem.AVATAR_UNSUPPORTED_TYPE);
    }

    @Test
    void aPictureTooBigInPixelsIsRefusedBeforeItIsDecoded() {
        // A header announcing 30000 x 30000 (900 MP) in a file of 45 bytes: reading the dimensions is enough to say no.
        assertThat(refusalOf(TestImages.pngHeaderOnly(30_000, 30_000))).isEqualTo(Problem.AVATAR_DIMENSIONS_TOO_LARGE);
        assertThat(refusalOf(TestImages.pngHeaderOnly(AvatarProcessor.MAX_SIDE + 1, 10)))
                .isEqualTo(Problem.AVATAR_DIMENSIONS_TOO_LARGE);
        assertThat(refusalOf(TestImages.png(new BufferedImage(AvatarProcessor.MAX_SIDE + 1, 2, BufferedImage.TYPE_INT_RGB))))
                .isEqualTo(Problem.AVATAR_DIMENSIONS_TOO_LARGE);
    }

    @Test
    void aTruncatedFileIsUnreadable() {
        byte[] png = TestImages.png(TestImages.halves(400, 400, Color.RED, Color.BLUE));
        assertThat(refusalOf(Arrays.copyOf(png, png.length / 2))).isEqualTo(Problem.AVATAR_UNREADABLE);

        byte[] jpeg = TestImages.jpeg(TestImages.halves(400, 400, Color.RED, Color.BLUE));
        assertThat(refusalOf(Arrays.copyOf(jpeg, jpeg.length / 2))).isEqualTo(Problem.AVATAR_UNREADABLE);
    }

    @Test
    void justTheMagicBytesAreNotAPicture() {
        assertThat(refusalOf(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02, 0x03}))
                .isEqualTo(Problem.AVATAR_UNREADABLE);
        assertThat(refusalOf(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0}))
                .isEqualTo(Problem.AVATAR_UNREADABLE);
    }

    @Test
    void aCorruptPngBodyIsUnreadable() {
        byte[] png = TestImages.png(TestImages.halves(100, 100, Color.RED, Color.BLUE));
        for (int i = 60; i < png.length - 20; i++) {
            png[i] = (byte) (png[i] ^ 0x5A);
        }
        assertThat(refusalOf(png)).isEqualTo(Problem.AVATAR_UNREADABLE);
    }

    @Test
    void whatIsAppendedAfterThePictureNeverReachesTheOutput() {
        // A valid JPEG followed by a script and a ZIP header (a polyglot): the picture is decoded, the rest is dropped.
        byte[] jpeg = TestImages.jpeg(TestImages.halves(300, 300, Color.RED, Color.BLUE));
        byte[] payload = "<script>alert('xss')</script>PK\u0003\u0004".getBytes(StandardCharsets.ISO_8859_1);
        byte[] polyglot = Arrays.copyOf(jpeg, jpeg.length + payload.length);
        System.arraycopy(payload, 0, polyglot, jpeg.length, payload.length);

        byte[] out = processor.process(polyglot).content();

        assertThat(TestImages.contains(out, "<script>")).isFalse();
        assertThat(TestImages.contains(out, "PK\u0003\u0004")).isFalse();
        assertThat(TestImages.decode(out).getWidth()).isEqualTo(512);
    }

    @Test
    void aPngDisguisedAsAnythingIsStillJudgedByItsContent() {
        // The processor never sees a file name; this is the contract: a real PNG is accepted, whatever it was called.
        assertThat(processor.process(TestImages.png(TestImages.halves(50, 50, Color.RED, Color.BLUE))).content()).isNotEmpty();
        assertThatThrownBy(() -> processor.process("not an image at all".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(AvatarException.class);
    }
}
