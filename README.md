# AI Control Plane - Refund Security & Sanitization

Dự án xây dựng tầng kiểm soát bảo mật (Control Plane) cho hệ thống tiếp nhận và phân tích yêu cầu hoàn tiền tự động bằng AI.

## Các biện pháp bảo vệ Prompt Injection (Sanitizer)

Thành phần `PromptInjectionDetector` thực hiện kiểm tra văn bản đầu vào (`reason`) trước khi đưa vào AI pipeline nhằm ngăn ngừa các cuộc tấn công prompt injection:
1. **Chuẩn hóa nâng cao (Normalization)**:
   - Loại bỏ hoặc thay thế các ký tự định dạng / ký tự ẩn Unicode `\p{Cf}` (zero-width space, BOM, soft hyphen,...).
   - Chuẩn hóa Unicode NFKC chuyển đổi các ký tự full-width sang chuẩn ASCII.
   - Chuyển về chữ thường (`toLowerCase`).
   - Chuyển `đ` thành `d`.
   - Chuẩn hóa NFD và loại bỏ các dấu tiếng Việt (`\p{M}`).
   - Gộp các khoảng trắng thừa.
2. **Bộ quy tắc đa tầng**:
   - Nhận diện các yêu cầu ép AI bỏ qua/quên chỉ dẫn trước đó (tiếng Anh & tiếng Việt).
   - Ngăn chặn trích xuất system prompt nội bộ.
   - Ngăn chặn mạo danh vai trò (Roleplay/Persona).
   - Ngăn chặn can thiệp trực tiếp hành động tài chính.
   - Ngăn chặn chèn thẻ cấu trúc giả mạo (`<|im_start|>`, `system:`).

## Đánh đổi & Giới hạn thiết kế (Design Trade-offs & Limitations)

### 1. Đánh đổi với các câu lệnh can thiệp tài chính
- Các quy tắc như `RULE_FINANCIAL_OVERRIDE_VI` (`hãy duyệt hoàn tiền`, `tự động duyệt hoàn tiền`,...) hoặc `RULE_FINANCIAL_OVERRIDE_EN` (`approve this refund`) được thiết kế để ngăn chặn việc người dùng ra lệnh trực tiếp cho AI tự động duyệt tiền.
- **Đánh đổi**: Một số trường hợp khách hàng lịch sự hoặc khẩn thiết viết những câu như *"Mong shop duyệt hoàn tiền giúp em"* có thể có rủi ro bị nghi ngờ.
- **Hướng cải tiến tương lai**: Thay vì từ chối thẳng (`REQUEST_REJECTED`), hệ thống ở các bước hoàn thiện có thể đổi sang cơ chế **Gắn cờ & chuyển duyệt thủ công (Flag for Human Review)**.

### 2. Đánh đổi với tiền tố vai trò (RULE_ROLE_PREFIX)
- Để ngăn chặn việc chèn tiền tố vai trò (`system:`, `assistant:`), regex đã dùng lookbehind để loại trừ các cụm danh từ phổ biến (`the system:`, `this system:`).
- **Đánh đổi**: Các câu tự nhiên của khách hàng mô tả lỗi hệ thống như *"Lỗi ở the system: tôi thanh toán bị trừ tiền 2 lần"* cần được kiểm thử cẩn trọng để không bị chặn nhầm.

### 3. Giới hạn của phương pháp Regex-based Detection
- **Ký tự đồng dạng (Homoglyphs)**: Regex hiện tại chưa xử lý triệt để việc thay thế ký tự bằng bảng chữ cái khác (ví dụ chữ Cyrillic `о` thay cho chữ Latin `o`).
- **Tách chữ bằng dấu cách**: Các trường hợp phân tách từng chữ cái như `i g n o r e` cần thêm bộ tiền xử lý gom chữ hoặc mô hình phân loại học máy chuyên biệt (ML-based Classifier / Guardrail model).
