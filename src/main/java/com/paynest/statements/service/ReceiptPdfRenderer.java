package com.paynest.statements.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynest.common.ErrorCodes;
import com.paynest.exception.ApplicationException;
import com.paynest.statements.dto.ReceiptDocument;
import com.paynest.statements.dto.ReceiptStyle;
import com.paynest.statements.dto.ReceiptTemplate;
import com.paynest.statements.dto.ReceiptTemplateElement;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ReceiptPdfRenderer {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*}}");

    private final ObjectMapper objectMapper;

    public byte[] render(ReceiptDocument document, ReceiptTemplate template) {
        Map<String, Object> data = objectMapper.convertValue(document, MAP_TYPE);
        try (PDDocument pdfDocument = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(resolvePageSize(template));
            pdfDocument.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(pdfDocument, page)) {
                drawPageBackground(contentStream, page, template);
                for (ReceiptTemplateElement element : template.getElements()) {
                    if (Boolean.FALSE.equals(element.getVisible())) {
                        continue;
                    }
                    drawElement(contentStream, page, template, element, data);
                }
            }

            pdfDocument.save(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new ApplicationException(
                    ErrorCodes.STATEMENT_GENERATION_FAILED,
                    "Unable to generate receipt PDF"
            );
        }
    }

    private PDRectangle resolvePageSize(ReceiptTemplate template) {
        if (template.getPage() == null || template.getPage().getSize() == null) {
            return PDRectangle.A4;
        }
        return switch (template.getPage().getSize().toUpperCase()) {
            case "LETTER" -> PDRectangle.LETTER;
            case "A4" -> PDRectangle.A4;
            default -> PDRectangle.A4;
        };
    }

    private void drawPageBackground(
            PDPageContentStream contentStream,
            PDPage page,
            ReceiptTemplate template
    ) throws IOException {
        if (template.getPage() == null || template.getPage().getBackgroundColor() == null) {
            return;
        }
        contentStream.setNonStrokingColor(parseColor(template.getPage().getBackgroundColor()));
        contentStream.addRect(
                0,
                0,
                page.getMediaBox().getWidth(),
                page.getMediaBox().getHeight()
        );
        contentStream.fill();
    }

    private void drawElement(
            PDPageContentStream contentStream,
            PDPage page,
            ReceiptTemplate template,
            ReceiptTemplateElement element,
            Map<String, Object> data
    ) throws IOException {
        String type = element.getType() == null ? "text" : element.getType();
        switch (type.toLowerCase()) {
            case "rect" -> drawRect(contentStream, page, template, element);
            case "line" -> drawLine(contentStream, page, template, element);
            case "field" -> drawText(contentStream, page, template, element,
                    formatValue(resolveFieldValue(data, element.getField()), element.getFormat()));
            case "fieldgroup" -> drawText(contentStream, page, template, element,
                    resolveTemplateText(data, element.getTemplate()));
            case "text" -> drawText(contentStream, page, template, element, element.getText());
            default -> throw new ApplicationException(
                    ErrorCodes.STATEMENT_TEMPLATE_NOT_FOUND,
                    "Unsupported receipt template element type " + type
            );
        }
    }

    private void drawRect(
            PDPageContentStream contentStream,
            PDPage page,
            ReceiptTemplate template,
            ReceiptTemplateElement element
    ) throws IOException {
        float x = valueOrZero(element.getX());
        float y = toPdfY(page, element.getY(), element.getHeight());
        float width = valueOrZero(element.getWidth());
        float height = valueOrZero(element.getHeight());
        ReceiptStyle style = resolveStyle(template, element);

        String fillColor = firstPresent(element.getFillColor(), style.getFillColor());
        if (fillColor != null) {
            contentStream.setNonStrokingColor(parseColor(fillColor));
            contentStream.addRect(x, y, width, height);
            contentStream.fill();
        }

        String strokeColor = firstPresent(element.getStrokeColor(), style.getStrokeColor());
        if (strokeColor != null) {
            contentStream.setStrokingColor(parseColor(strokeColor));
            contentStream.setLineWidth(valueOrDefault(element.getLineWidth(), style.getLineWidth(), 1F));
            contentStream.addRect(x, y, width, height);
            contentStream.stroke();
        }
    }

    private void drawLine(
            PDPageContentStream contentStream,
            PDPage page,
            ReceiptTemplate template,
            ReceiptTemplateElement element
    ) throws IOException {
        ReceiptStyle style = resolveStyle(template, element);
        contentStream.setStrokingColor(parseColor(firstPresent(element.getStrokeColor(), style.getStrokeColor(), style.getColor())));
        contentStream.setLineWidth(valueOrDefault(element.getLineWidth(), style.getLineWidth(), 1F));

        float startX = valueOrZero(element.getX());
        float startY = toPdfY(page, element.getY(), 0F);
        float endX = startX + valueOrZero(element.getWidth());
        float endY = startY - valueOrZero(element.getHeight());

        contentStream.moveTo(startX, startY);
        contentStream.lineTo(endX, endY);
        contentStream.stroke();
    }

    private void drawText(
            PDPageContentStream contentStream,
            PDPage page,
            ReceiptTemplate template,
            ReceiptTemplateElement element,
            String text
    ) throws IOException {
        String safeText = sanitizeText(text);
        if (safeText == null || safeText.isBlank()) {
            return;
        }
        ReceiptStyle style = resolveStyle(template, element);
        PDFont font = resolveFont(style.getFont());
        float fontSize = style.getFontSize() == null ? 10F : style.getFontSize();
        float x = resolveAlignedX(element, safeText, font, fontSize);
        float y = toPdfY(page, element.getY(), 0F) - fontSize;

        contentStream.beginText();
        contentStream.setFont(font, fontSize);
        contentStream.setNonStrokingColor(parseColor(style.getColor()));
        contentStream.newLineAtOffset(x, y);
        contentStream.showText(safeText);
        contentStream.endText();
    }

    private float resolveAlignedX(
            ReceiptTemplateElement element,
            String text,
            PDFont font,
            float fontSize
    ) throws IOException {
        float x = valueOrZero(element.getX());
        float width = valueOrZero(element.getWidth());
        if (width <= 0 || element.getAlign() == null) {
            return x;
        }

        float textWidth = font.getStringWidth(text) / 1000F * fontSize;
        return switch (element.getAlign().toLowerCase()) {
            case "center" -> x + Math.max((width - textWidth) / 2F, 0F);
            case "right" -> x + Math.max(width - textWidth, 0F);
            default -> x;
        };
    }

    private ReceiptStyle resolveStyle(ReceiptTemplate template, ReceiptTemplateElement element) {
        if (template.getStyles() == null || element.getStyle() == null) {
            return new ReceiptStyle();
        }
        return template.getStyles().getOrDefault(element.getStyle(), new ReceiptStyle());
    }

    private Object resolveFieldValue(Map<String, Object> data, String fieldPath) {
        if (fieldPath == null || fieldPath.isBlank()) {
            return null;
        }
        Object value = data;
        for (String pathPart : fieldPath.split("\\.")) {
            if (!(value instanceof Map<?, ?> map)) {
                return null;
            }
            value = map.get(pathPart);
            if (value == null) {
                return null;
            }
        }
        return value;
    }

    private String resolveTemplateText(Map<String, Object> data, String template) {
        if (template == null) {
            return "";
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            Object value = resolveFieldValue(data, matcher.group(1));
            matcher.appendReplacement(builder, Matcher.quoteReplacement(formatValue(value, null)));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private String formatValue(Object value, String format) {
        if (value == null) {
            return "";
        }
        if ("amount".equalsIgnoreCase(format) && value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue())
                    .setScale(2, RoundingMode.HALF_UP)
                    .toPlainString();
        }
        if ("uppercase".equalsIgnoreCase(format)) {
            return value.toString().toUpperCase();
        }
        return value.toString();
    }

    private String sanitizeText(String text) {
        if (text == null) {
            return null;
        }
        String normalizedText = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalizedText.replaceAll("[^\\x20-\\x7E]", "?");
    }

    private PDFont resolveFont(String fontName) {
        if (fontName == null) {
            return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        }
        return switch (fontName.toUpperCase()) {
            case "HELVETICA_BOLD" -> new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            case "HELVETICA_OBLIQUE" -> new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
            case "COURIER" -> new PDType1Font(Standard14Fonts.FontName.COURIER);
            case "COURIER_BOLD" -> new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD);
            case "TIMES_ROMAN" -> new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
            case "TIMES_BOLD" -> new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);
            default -> new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        };
    }

    private Color parseColor(String hexColor) {
        if (hexColor == null || hexColor.isBlank()) {
            return Color.BLACK;
        }
        return Color.decode(hexColor.trim());
    }

    private float toPdfY(PDPage page, Float templateY, Float height) {
        return page.getMediaBox().getHeight() - valueOrZero(templateY) - valueOrZero(height);
    }

    private float valueOrZero(Float value) {
        return value == null ? 0F : value;
    }

    private float valueOrDefault(Float firstValue, Float secondValue, Float fallback) {
        if (firstValue != null) {
            return firstValue;
        }
        if (secondValue != null) {
            return secondValue;
        }
        return fallback;
    }

    private String firstPresent(String firstValue, String secondValue) {
        return firstValue == null || firstValue.isBlank() ? secondValue : firstValue;
    }

    private String firstPresent(String firstValue, String secondValue, String thirdValue) {
        String firstResult = firstPresent(firstValue, secondValue);
        return firstPresent(firstResult, thirdValue);
    }
}
