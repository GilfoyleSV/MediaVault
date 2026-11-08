package ru.kuzmin.mediavault.service;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

final class ThumbnailImage {
    private static final int MAX_EDGE = 320;
    private static final long MAX_PIXELS = 25_000_000;

    private ThumbnailImage() {}

    static byte[] create(byte[] source, String mimeType) throws IOException {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(source))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("Unrecognized image format");
            }
            var reader = readers.next();
            try {
                String expected = "image/png".equalsIgnoreCase(mimeType) ? "png" : "jpeg";
                if (!expected.equalsIgnoreCase(reader.getFormatName())) {
                    throw new IOException("Image format differs from the declared content type");
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
                    throw new IOException("Image dimensions exceed the thumbnail input limit");
                }

                int largestEdge = Math.max(width, height);
                int sample = Math.max(1, largestEdge / MAX_EDGE);
                var params = reader.getDefaultReadParam();
                params.setSourceSubsampling(sample, sample, 0, 0);
                double scale = Math.min(1.0, (double) MAX_EDGE / largestEdge);
                return encode(reader.read(0, params), Math.max(1, (int) Math.round(width * scale)),
                        Math.max(1, (int) Math.round(height * scale)));
            } finally {
                reader.dispose();
            }
        }
    }

    private static byte[] encode(BufferedImage source, int width, int height) throws IOException {
        var thumbnail = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = thumbnail.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        var output = new ByteArrayOutputStream();
        if (!ImageIO.write(thumbnail, "jpeg", output)) {
            throw new IOException("JPEG encoder is unavailable");
        }
        return output.toByteArray();
    }
}
