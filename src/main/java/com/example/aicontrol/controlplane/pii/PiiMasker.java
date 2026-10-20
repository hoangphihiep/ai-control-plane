package com.example.aicontrol.controlplane.pii;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thành phần che (mask) thông tin định danh cá nhân (PII) trước khi gửi cho AI bên ngoài.
 *
 * Lưu ý kiến trúc:
 * - Trường customerPhone ở DTO tuyệt đối không được đưa vào prompt gửi cho AI ở bất kỳ dạng nào
 *   (kể cả dạng masked), tuân thủ nguyên tắc Privacy by Design và Zero Trust.
 * - PiiMasker chỉ phục vụ che các thông tin PII xuất hiện tự do trong văn bản lý do hoàn tiền (reason).
 */
@Component
public class PiiMasker {

    // 1. Số thẻ thanh toán (13 đến 19 chữ số): có thể phân tách bằng khoảng trắng hoặc dấu gạch ngang
    private static final Pattern CARD_PATTERN = Pattern.compile(
            "(?<!\\d[ -])(?<![\\w-])\\d(?:[ -]?\\d){12,18}(?![\\w@])(?![ -]?\\d)"
    );

    // 2. Căn cước công dân (12 chữ số): có thể viết liền hoặc phân tách từng cụm số
    private static final Pattern ID_PATTERN = Pattern.compile(
            "(?<!\\d[ -])(?<![\\w-])\\d(?:[ -]?\\d){11}(?![\\w@])(?![ -]?\\d)"
    );

    // 3. Email (địa chỉ thư điện tử)
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "(?<![\\w.+-])[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"
    );

    // 4. Số điện thoại Việt Nam (10 chữ số bắt đầu bằng 0, hoặc tiền tố +84 / (+84))
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?<![\\w.+-])(?:\\(\\+84\\)[ .-]?(?:0[ .-]?)?|\\+84[ .-]?(?:0[ .-]?)?|0)[ .-]?[35789](?:[ .-]?[0-9]){8}(?![\\w@])(?![ .-]\\d)(?!-[a-zA-Z])"
    );

    /**
     * Che thông tin PII trong văn bản theo đúng thứ tự ưu tiên:
     * Số thẻ -> CCCD -> Email -> Số điện thoại.
     *
     * @param text Chuỗi văn bản đầu vào (ví dụ trường reason)
     * @return MaskedResult chứa văn bản đã che và bảng mapping (chỉ lưu trong memory)
     */
    public MaskedResult mask(String text) {
        if (text == null || text.isBlank()) {
            return new MaskedResult(text, Map.of());
        }

        // 1. Loại bỏ các ký tự định dạng ẩn Unicode (\p{Cf}: zero-width space, soft hyphen, BOM,...)
        // Xóa rỗng để nối liền các chữ số bị chèn ký tự ẩn chia tách
        String cleanedText = text.replaceAll("\\p{Cf}", "");

        // 2. Chuẩn hóa Unicode NFKC để chuyển đổi các chữ số full-width (０-９) về chữ số ASCII chuẩn
        String currentText = Normalizer.normalize(cleanedText, Normalizer.Form.NFKC);

        Map<String, String> mapping = new LinkedHashMap<>();

        // 1. Thẻ thanh toán (13-19 chữ số)
        currentText = maskEntity(currentText, CARD_PATTERN, "CARD", mapping, this::canonicalizeDigits);

        // 2. CCCD (12 chữ số)
        currentText = maskEntity(currentText, ID_PATTERN, "ID", mapping, this::canonicalizeDigits);

        // 3. Email
        currentText = maskEntity(currentText, EMAIL_PATTERN, "EMAIL", mapping, this::canonicalizeEmail);

        // 4. Số điện thoại Việt Nam
        currentText = maskEntity(currentText, PHONE_PATTERN, "PHONE", mapping, this::canonicalizePhone);

        return new MaskedResult(currentText, mapping);
    }

    /**
     * Khôi phục văn bản từ văn bản đã che và bảng mapping (dùng khi hiển thị cho người duyệt).
     *
     * Lưu ý quan trọng về thiết kế (Design Trade-off):
     * Phương thức này khôi phục theo dạng chuẩn hóa, không bảo toàn từng ký tự gốc.
     * Cụ thể:
     * - Nếu cùng một thực thể PII xuất hiện ở nhiều dạng phân tách khác nhau trong văn bản gốc
     *   (ví dụ: vừa có "4111 1111 1111 1111" vừa có "4111-1111-1111-1111"), khi khôi phục cả hai vị trí đều
     *   sẽ nhận giá trị của lần xuất hiện đầu tiên được lưu trong mapping.
     * - Các chữ số full-width (０-９) hoặc ký tự ẩn (\p{Cf}) sau khi mask và restore sẽ trở thành
     *   chữ số ASCII chuẩn và sạch ký tự ẩn.
     *
     * @param maskedText Văn bản đã che
     * @param mapping    Bảng ánh xạ placeholder -> giá trị gốc
     * @return Văn bản đã khôi phục
     */
    public String restore(String maskedText, Map<String, String> mapping) {
        if (maskedText == null || mapping == null || mapping.isEmpty()) {
            return maskedText;
        }

        String restored = maskedText;
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            restored = restored.replace(entry.getKey(), entry.getValue());
        }
        return restored;
    }

    /**
     * Khôi phục văn bản gốc từ đối tượng MaskedResult.
     */
    public String restore(MaskedResult maskedResult) {
        if (maskedResult == null) {
            return null;
        }
        return restore(maskedResult.maskedText(), maskedResult.mapping());
    }

    @FunctionalInterface
    private interface Canonicalizer {
        String canonicalize(String raw);
    }

    private String maskEntity(String text, Pattern pattern, String prefix, Map<String, String> mapping, Canonicalizer canonicalizer) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return text;
        }

        Map<String, String> canonicalToPlaceholder = new LinkedHashMap<>();
        int counter = 0;

        StringBuilder sb = new StringBuilder();
        do {
            String rawMatch = matcher.group();
            String canonical = canonicalizer.canonicalize(rawMatch);

            String placeholder = canonicalToPlaceholder.get(canonical);
            if (placeholder == null) {
                counter++;
                placeholder = "[" + prefix + "_" + counter + "]";
                canonicalToPlaceholder.put(canonical, placeholder);
                mapping.put(placeholder, rawMatch);
            }

            matcher.appendReplacement(sb, Matcher.quoteReplacement(placeholder));
        } while (matcher.find());

        matcher.appendTail(sb);
        return sb.toString();
    }

    private String canonicalizeDigits(String raw) {
        return raw.replaceAll("[^0-9]", "");
    }

    private String canonicalizeEmail(String raw) {
        return raw.toLowerCase(Locale.ROOT).trim();
    }

    private String canonicalizePhone(String raw) {
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("84")) {
            digits = digits.substring(2);
        }
        if (!digits.startsWith("0")) {
            digits = "0" + digits;
        }
        return digits;
    }
}
