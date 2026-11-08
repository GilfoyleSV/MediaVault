package ru.kuzmin.mediavault.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThumbnailImageTest {
    @Test
    void shrinksPngAndPreservesAspectRatio() throws IOException {
        byte[] thumbnail = ThumbnailImage.create(png(), "image/png");
        var decoded = ImageIO.read(new ByteArrayInputStream(thumbnail));

        assertThat(thumbnail).startsWith((byte) 0xff, (byte) 0xd8);
        assertThat(decoded.getWidth()).isEqualTo(320);
        assertThat(decoded.getHeight()).isEqualTo(160);
    }

    @Test
    void rejectsPngDeclaredAsJpeg() throws IOException {
        byte[] png = png();
        assertThatThrownBy(() -> ThumbnailImage.create(png, "image/jpeg"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("declared content type");
    }

    private static byte[] png() throws IOException {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(800, 400, BufferedImage.TYPE_INT_ARGB), "png", output);
        return output.toByteArray();
    }
}
