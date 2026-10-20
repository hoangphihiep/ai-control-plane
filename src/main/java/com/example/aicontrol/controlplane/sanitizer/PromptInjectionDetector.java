package com.example.aicontrol.controlplane.sanitizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class PromptInjectionDetector {

    private static final Logger log = LoggerFactory.getLogger(PromptInjectionDetector.class);

    public record InjectionRule(String id, Pattern pattern) {}

    private static final List<InjectionRule> RULES = List.of(
            // 1. Bảo AI bỏ qua/quên hướng dẫn trước đó (tiếng Anh) - hỗ trợ các từ nối như 'the', 'any', 'all of the'
            // Yêu cầu có định từ chỉ định AI (previous, prior, above, system, your) để tránh chặn nhầm 'ignore the instructions in my email'
            new InjectionRule(
                    "RULE_IGNORE_PREVIOUS_EN",
                    Pattern.compile("\\b(ignore|disregard|forget)\\s+((all|any)\\s+)?(of\\s+)?(the\\s+)?(previous|prior|above|system)\\s+(instructions?|prompts?|directives?)\\b|\\b(ignore|disregard|forget)\\s+((all|any)\\s+)?(of\\s+)?your\\s+(instructions?|prompts?|rules?|directives?)\\b")
            ),
            // 2. Bảo AI bỏ qua/quên hướng dẫn trước đó (tiếng Việt không dấu) - gắn chặt với ngữ cảnh AI
            new InjectionRule(
                    "RULE_IGNORE_PREVIOUS_VI",
                    Pattern.compile("\\b(bo qua|quen di)\\s+(tat ca\\s+|moi\\s+)?(cac\\s+)?(huong dan|chi dan|quy tac|cau lenh|prompt)\\s+(truoc|ban dau|he thong|cua he thong)\\b|\\b(bo qua|quen di)\\s+(moi\\s+|tat ca\\s+)?(quy tac|huong dan)\\s+cua\\s+he\\s+thong\\b")
            ),
            // 3. Hỏi hoặc đòi in ra system prompt (tiếng Anh) - hỗ trợ thêm 'me', 'the'
            new InjectionRule(
                    "RULE_SYSTEM_PROMPT_EN",
                    Pattern.compile("\\b(show|reveal|print|display|output|what is)\\s+(me\\s+)?(your\\s+|the\\s+)?(system prompt|initial prompt|internal prompt)\\b|\\b(repeat|print|output)\\s+(the\\s+)?(words\\s+above|prompt above|instructions above)\\b")
            ),
            // 4. Hỏi hoặc đòi in ra system prompt (tiếng Việt không dấu)
            new InjectionRule(
                    "RULE_SYSTEM_PROMPT_VI",
                    Pattern.compile("\\b(in ra|hien thi|tiet lo|cho xem|cho biet)\\s+(system prompt|prompt goc|prompt he thong|huong dan he thong|prompt ban dau)\\b")
            ),
            // 5. Bảo AI đóng vai khác (tiếng Anh) - bắt buộc có danh từ vai trò sau a/an, tránh bắt 'you are now' trần
            new InjectionRule(
                    "RULE_ROLEPLAY_EN",
                    Pattern.compile("\\b(you are now (a|an)\\s+\\w+|from now on you are (a|an)?\\s+\\w+|pretend to be (a|an)\\s+\\w+|roleplay as (a|an)\\s+\\w+|act as (a|an)\\s+\\w+)\\b")
            ),
            // 6. Bảo AI đóng vai khác (tiếng Việt không dấu) - tránh các từ đời thường như 'tro thanh', 'hay la mot'
            new InjectionRule(
                    "RULE_ROLEPLAY_VI",
                    Pattern.compile("\\b(tu gio ban la|ke tu bay gio ban la|hay dong vai (la\\s+)?(mot)?)\\b")
            ),
            // 7. Ra lệnh hành động tài chính trực tiếp (tiếng Anh)
            new InjectionRule(
                    "RULE_FINANCIAL_OVERRIDE_EN",
                    Pattern.compile("\\b(approve this refund|approve the refund|auto approve refund|immediately approve refund|always approve refund|approve refund immediately)\\b")
            ),
            // 8. Ra lệnh hành động tài chính trực tiếp (tiếng Việt không dấu)
            new InjectionRule(
                    "RULE_FINANCIAL_OVERRIDE_VI",
                    Pattern.compile("\\b(hay duyet hoan tien|duyet hoan tien ngay|tu dong duyet hoan tien|bat buoc phai duyet hoan tien)\\b")
            ),
            // 9. Chèn thẻ phân tách vai trò giả mạo
            new InjectionRule(
                    "RULE_DELIMITER_TAGS",
                    Pattern.compile("<\\|im_start\\|>|<\\|im_end\\|>|<\\|system\\|>|\\[system\\]|\\[inst\\]|\\[/inst\\]|```system")
            ),
            // 10. Tiền tố phân vai hệ thống (loại trừ trường hợp danh từ 'the system:', 'this system:')
            new InjectionRule(
                    "RULE_ROLE_PREFIX",
                    Pattern.compile("(^|(?<!\\b(the|this|that|our|a|an))\\s)(system|assistant)\\s*:")
            )
    );

    /**
     * Kiểm tra văn bản đầu vào xem có dấu hiệu tấn công Prompt Injection hay không.
     * Sử dụng 2 phiên bản văn bản đã chuẩn hóa:
     * 1. Ký tự ẩn Unicode (\p{Cf}) bị xóa rỗng (chống chèn ký tự ẩn giữa các ký tự trong từ)
     * 2. Ký tự ẩn Unicode (\p{Cf}) đổi thành khoảng trắng (chống dùng ký tự ẩn thay thế khoảng trắng giữa các từ)
     *
     * @param text Văn bản cần kiểm tra
     */
    public void detect(String text) {
        if (text == null || text.isBlank()) {
            return;
        }

        String normalizedEmpty = normalize(text, false);
        String normalizedSpace = normalize(text, true);

        for (InjectionRule rule : RULES) {
            if (rule.pattern().matcher(normalizedEmpty).find() || rule.pattern().matcher(normalizedSpace).find()) {
                // CHỈ ghi log rule ID, TUYỆT ĐỐI không ghi nội dung gốc text/reason vì có thể chứa PII
                log.warn("Prompt injection detected by rule: {}", rule.id());
                throw new SuspiciousInputException(rule.id());
            }
        }
    }

    /**
     * Chuẩn hóa văn bản mặc định (xóa ký tự ẩn).
     *
     * @param text Chuỗi văn bản thô
     * @return Chuỗi văn bản đã chuẩn hóa
     */
    public String normalize(String text) {
        return normalize(text, false);
    }

    /**
     * Chuẩn hóa văn bản trước khi so khớp:
     * 1. Xử lý nhóm ký tự định dạng / ký tự ẩn Unicode (\p{Cf}: zero-width, BOM, soft hyphen...)
     * 2. Chuẩn hóa Unicode NFKC (chuyển full-width sang chuẩn ASCII)
     * 3. Chuyển về chữ thường
     * 4. Đổi đ thành d (không cần Đ vì đã toLowerCase trước đó)
     * 5. Chuẩn hóa NFD và xóa \p{M} để bỏ dấu tiếng Việt
     * 6. Gộp nhiều khoảng trắng thành một khoảng trắng đơn và trim
     *
     * @param text Chuỗi văn bản thô
     * @param replaceHiddenWithSpace Nếu true, thay ký tự ẩn \p{Cf} bằng khoảng trắng; nếu false, xóa rỗng
     * @return Chuỗi văn bản đã chuẩn hóa
     */
    public String normalize(String text, boolean replaceHiddenWithSpace) {
        if (text == null) {
            return "";
        }

        // 1. Ký tự định dạng / ẩn Unicode (\p{Cf}: \u200B, \u200C, \u200D, \uFEFF, \u2060, \u00AD, \u200E...)
        String replacement = replaceHiddenWithSpace ? " " : "";
        String normalized = text.replaceAll("\\p{Cf}", replacement);

        // 2. Chuẩn hóa Unicode NFKC
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFKC);

        // 3. Chuyển về chữ thường
        normalized = normalized.toLowerCase(Locale.ROOT);

        // 4. Chuyển đ thành d (không cần Đ vì đã toLowerCase trước đó)
        normalized = normalized.replace('đ', 'd');

        // 5. Chuẩn hóa NFD và xóa \p{M} để bỏ dấu tiếng Việt
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}", "");

        // 6. Gộp nhiều khoảng trắng thành một và trim hai đầu
        normalized = normalized.replaceAll("\\s+", " ").trim();

        return normalized;
    }
}
