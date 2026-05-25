package com.paynest.documents.service;

import com.paynest.common.ErrorCodes;
import com.paynest.exception.ApplicationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

@Service
public class DocumentThumbnailService {

    private static final int MAX_DIMENSION = 320;
    private static final float JPEG_QUALITY = 0.82f;

    public ThumbnailData generate(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            BufferedImage source = ImageIO.read(input);
            if (source == null) {
                throw invalidImage();
            }

            double scale = Math.min(
                    1.0d,
                    Math.min(
                            (double) MAX_DIMENSION / source.getWidth(),
                            (double) MAX_DIMENSION / source.getHeight()
                    )
            );
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            BufferedImage thumbnail = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = thumbnail.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.drawImage(source, 0, 0, width, height, null);
            } finally {
                graphics.dispose();
            }

            return new ThumbnailData(writeJpeg(thumbnail), "image/jpeg");
        } catch (IOException ex) {
            throw invalidImage();
        }
    }

    private byte[] writeJpeg(BufferedImage image) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("JPEG writer unavailable");
        }

        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            if (parameters.canWriteCompressed()) {
                parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                parameters.setCompressionQuality(JPEG_QUALITY);
            }
            writer.write(null, new IIOImage(image, null, null), parameters);
            return output.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private ApplicationException invalidImage() {
        return new ApplicationException(
                ErrorCodes.INVALID_REQUEST,
                "Uploaded image cannot be decoded for thumbnail generation",
                HttpStatus.BAD_REQUEST
        );
    }

    public record ThumbnailData(byte[] content, String contentType) {
    }
}
