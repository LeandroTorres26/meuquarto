package com.meuquarto.profile.domain;

import com.meuquarto.profile.domain.AvatarException.Problem;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * Turns an uploaded picture into the avatar we store (FR-05, docs/security.md "Malicious uploads").
 * In order:
 * <ol>
 *   <li>size limit, then the type from the magic bytes (JPEG or PNG only);</li>
 *   <li>the dimensions are read from the header and refused above {@link #MAX_SIDE} before anything is
 *       decoded, so a tiny file that expands to gigabytes (a decompression bomb) never gets decoded;</li>
 *   <li>the picture is decoded (already shrunk while decoding when it is much bigger than the avatar),
 *       cropped to a centered square and resized to {@link #OUTPUT_SIZE}, turned upright by its EXIF
 *       orientation;</li>
 *   <li>re-encoded as a new JPEG on a white background. Nothing of the original file survives: not its
 *       metadata (EXIF, GPS, comments), not trailing bytes, not an embedded second format.</li>
 * </ol>
 * Plain Java (JDK only), no Spring, so it is unit tested without the framework.
 */
public final class AvatarProcessor {

    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public static final int MAX_SIDE = 4096;
    public static final int OUTPUT_SIZE = 512;
    public static final String OUTPUT_TYPE = "image/jpeg";

    private static final float JPEG_QUALITY = 0.88f;

    /** The stored avatar: always a {@value #OUTPUT_SIZE} x {@value #OUTPUT_SIZE} JPEG. */
    public record Result(byte[] content, String contentType) {}

    /** @throws AvatarException with the reason when the upload cannot become an avatar */
    public Result process(byte[] upload) {
        if (upload == null || upload.length == 0) {
            throw new AvatarException(Problem.AVATAR_EMPTY);
        }
        if (upload.length > MAX_BYTES) {
            throw new AvatarException(Problem.AVATAR_TOO_LARGE);
        }
        ImageType type = ImageType.detect(upload)
                .orElseThrow(() -> new AvatarException(Problem.AVATAR_UNSUPPORTED_TYPE));
        try {
            BufferedImage decoded = decode(upload, type);
            int orientation = type == ImageType.JPEG ? ExifOrientation.of(upload) : 1;
            return new Result(encode(square(decoded, orientation)), OUTPUT_TYPE);
        } catch (AvatarException refused) {
            throw refused;
        } catch (IOException | RuntimeException e) {
            // A truncated or corrupt file, a variant the JDK cannot read (CMYK), or a decoder failure.
            throw new AvatarException(Problem.AVATAR_UNREADABLE);
        }
    }

    private BufferedImage decode(byte[] upload, ImageType type) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(type.readerFormat());
        if (!readers.hasNext()) {
            throw new AvatarException(Problem.AVATAR_UNREADABLE);
        }
        ImageReader reader = readers.next();
        // Memory cache on purpose: ImageIO's default file cache would write the upload to a temp file.
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(upload))) {
            reader.setInput(input, true, true);
            int width = reader.getWidth(0); // header only
            int height = reader.getHeight(0);
            if (width < 1 || height < 1) {
                throw new AvatarException(Problem.AVATAR_UNREADABLE);
            }
            if (width > MAX_SIDE || height > MAX_SIDE) {
                throw new AvatarException(Problem.AVATAR_DIMENSIONS_TOO_LARGE);
            }
            ImageReadParam param = reader.getDefaultReadParam();
            // Keep at least OUTPUT_SIZE pixels on the short side, but do not hold a 16 MP bitmap in memory.
            int step = Math.max(1, Math.min(width, height) / OUTPUT_SIZE);
            param.setSourceSubsampling(step, step, 0, 0);
            // The JPEG decoder reports a truncated or damaged file as a warning and returns what it has; an
            // avatar is not worth keeping half grey, so any warning counts as unreadable.
            AtomicBoolean damaged = new AtomicBoolean(false);
            reader.addIIOReadWarningListener((source, warning) -> damaged.set(true));
            BufferedImage image = reader.read(0, param);
            if (damaged.get()) {
                throw new AvatarException(Problem.AVATAR_UNREADABLE);
            }
            return image;
        } finally {
            reader.dispose();
        }
    }

    /** Centered square crop, scaled to {@link #OUTPUT_SIZE}, on white, turned upright. */
    private BufferedImage square(BufferedImage source, int orientation) {
        int side = Math.min(source.getWidth(), source.getHeight());
        int left = (source.getWidth() - side) / 2;
        int top = (source.getHeight() - side) / 2;

        BufferedImage target = new BufferedImage(OUTPUT_SIZE, OUTPUT_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, OUTPUT_SIZE, OUTPUT_SIZE);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.transform(ExifOrientation.upright(orientation, OUTPUT_SIZE));
            g.drawImage(source, 0, 0, OUTPUT_SIZE, OUTPUT_SIZE, left, top, left + side, top + side, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    /** A fresh JPEG: no metadata is passed to the writer, so none is written. */
    private byte[] encode(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageOutputStream output = new MemoryCacheImageOutputStream(out)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), param);
            output.flush();
            return out.toByteArray();
        } finally {
            writer.dispose();
        }
    }
}
