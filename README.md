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

## Cơ chế Che thông tin định danh cá nhân (PII Masking)

Thành phần `PiiMasker` thuộc package `com.example.aicontrol.controlplane.pii` đảm nhiệm việc che thông tin nhạy cảm của khách hàng trong trường `reason` trước khi chuyển tiếp cho mô hình AI bên ngoài phân tích:

1. **Các loại PII được nhận diện và che**:
   - **Số thẻ thanh toán (13 đến 19 chữ số)**: Thay bằng `[CARD_n]`, hỗ trợ các định dạng viết liền, phân tách bằng dấu cách hoặc gạch ngang (Visa, Mastercard, Amex,...).
   - **Căn cước công dân (12 chữ số)**: Thay bằng `[ID_n]`, hỗ trợ cả viết liền và phân cụm.
   - **Địa chỉ thư điện tử (Email)**: Thay bằng `[EMAIL_n]`, hỗ trợ đầy đủ các định dạng email chuẩn và tên miền Việt Nam (`.vn`, `.com.vn`).
   - **Số điện thoại Việt Nam (10 chữ số)**: Thay bằng `[PHONE_n]`, hỗ trợ các đầu số di động (`03`, `05`, `07`, `08`, `09`), các tiền tố quốc tế `+84`, `(+84)` và các dấu phân tách khoảng trắng, dấu chấm, dấu gạch ngang.

2. **Quy tắc xử lý cốt lõi**:
   - **Thứ tự ưu tiên**: `CARD` -> `ID` -> `EMAIL` -> `PHONE`. Thứ tự này loại trừ hoàn toàn rủi ro chuỗi số thẻ 16 số hoặc CCCD 12 số bị nhận diện nhầm và cắt vụn thành số điện thoại 10 số.
   - **Tái sử dụng placeholder (Canonical Deduplication)**: Các giá trị PII giống nhau trong cùng văn bản sẽ được gán cùng một placeholder (ví dụ cùng một số điện thoại dù xuất hiện ở định dạng `0912 345 678` hay `+84 912 345 678` đều nhận chung mã `[PHONE_1]`).
   - **Chuẩn hóa chữ số Full-Width**: Dùng Unicode NFKC chuyển đổi các chữ số dạng full-width (`０-９`) về chữ số ASCII chuẩn trước khi áp dụng regex.
   - **Ràng buộc chuỗi độc lập (Lookaround Constraints)**: Dùng lookbehind và lookahead nghiêm ngặt (`(?<!\d[ -])`, `(?![ -]?\d)`, `(?<![\w.+-])`, `(?![ -]?[a-zA-Z])`) để đảm bảo không che một phần của chuỗi dài hơn (như mã bưu kiện 20 số, mã đơn hàng `ORD-0912345678`).

3. **Bảo mật Logging & Cơ chế Restore**:
   - `MaskedResult` ghi đè phương thức `toString()` để **chỉ hiển thị `maskedText` và số lượng `mappingCount`**, tuyệt đối không in bảng `mapping` ra log để ngăn chặn rò rỉ PII khi dev vô tình log đối tượng này.
   - Bảng ánh xạ `mapping` (`placeholder -> giá trị gốc`) chỉ được lưu tạm thời trong bộ nhớ của request đó và giải phóng sau khi xử lý xong.
   - Hỗ trợ hàm `restore(MaskedResult)` và `restore(String maskedText, Map<String, String> mapping)` phục vụ việc hoàn nguyên văn bản gốc cho người duyệt (Human Reviewer) khi cần thiết.

## Quyết định kiến trúc về trường `customerPhone` (Privacy by Design)

- **Quyết định**: Trường `customerPhone` nhận từ DTO (`RefundRequest`) **tuyệt đối không được đưa vào prompt gửi cho AI bên ngoài ở bất kỳ hình thức nào** (kể cả dưới dạng đã che `[PHONE_n]`).
- **Lý do**:
  1. **Nguyên tắc Tối thiểu hóa Dữ liệu (Data Minimization - GDPR/Nghị định 13/2023/NĐ-CP)**: AI chỉ cần ngữ cảnh đơn hàng (`orderId`, `orderAmount`) và chi tiết sự cố trong `reason` để phân tích chính sách bồi hoàn; AI hoàn toàn không có nhu cầu nghiệp vụ biết số điện thoại của khách hàng.
  2. **Nguyên tắc Zero Trust**: Dữ liệu PII của khách hàng được cô lập hoàn toàn ở ranh giới API của hệ thống nội bộ, loại bỏ triệt để nguy cơ AI bên thứ ba thu thập hoặc vô tình lưu vết vào dữ liệu huấn luyện (Training Data Retention).

## Giới hạn của giải pháp PII Masking bằng Regex và Quyết định kỹ thuật

### 1. Giới hạn: Regex không bắt được Họ tên và Địa chỉ cá nhân
Hệ thống PII Masker hiện tại tập trung che 4 loại thông tin định danh có cấu trúc rõ ràng: **Số thẻ ngân hàng, CCCD, Email và Số điện thoại**. Phương pháp này **chưa thể che được Họ tên và Địa chỉ cá nhân**.

**Vì sao quyết định không dùng Regex để che Họ tên và Địa chỉ?**
- **Rủi ro Báo động giả (False Positive) cực kỳ cao đối với Họ tên tiếng Việt**: Tên người Việt Nam bao gồm các từ đơn âm tiết trùng hoàn toàn với từ vựng thông dụng trong ngôn ngữ tự nhiên (*Hoàng, Bình, Sang, Mai, Chi, Giang, Tiến, Tài, Minh, Hạnh,...*). Nếu áp dụng danh sách từ điển tên hoặc regex viết hoa tên riêng, các câu khiếu nại hoàn toàn bình thường của khách hàng như: *"Sản phẩm bình thường nhưng giao trễ"*, *"Ngày mai tôi đi công tác"*, *"Shop tiến hành hoàn tiền cho tôi"* sẽ bị che nhầm thành: *"Sản phẩm [NAME_1] nhưng giao trễ"*, *"Ngày [NAME_2] tôi đi công tác"*, *"Shop [NAME_3] hành hoàn tiền cho tôi"*. Điều này làm biến dạng ngữ nghĩa của lý do hoàn tiền khiến AI bên ngoài hiểu sai hoàn toàn ngữ cảnh.
- **Độ biến thiên và tính tự do của cấu trúc Địa chỉ tại Việt Nam**: Địa chỉ tại Việt Nam không tuân theo format cứng nhắc: có thể kết hợp số nhà, ngõ/ngách/kiệt/hẻm, tên đường, thôn/ấp/xóm/bản, xã/phường/thị trấn, quận/huyện/thị xã, tỉnh/thành phố với vô số cách viết tắt (*Q1, P. Bến Nghé, TX. Dĩ An, TP.HCM,...*). Regex tĩnh không thể phân định ranh giới giữa một địa chỉ nhà riêng với một địa danh/cửa hàng/kho hàng công cộng.
- **Giải pháp kiến trúc phù hợp**: Việc nhận diện Họ tên và Địa chỉ thuộc về bài toán **Nhận diện Thực thể Tên (Named Entity Recognition - NER)** của Xử lý Ngôn ngữ Tự nhiên (NLP). Giải pháp chuẩn xác cho bài toán này là sử dụng một mô hình NER học máy cục bộ (như PhoBERT-NER chạy On-Premise) để hiểu ngữ cảnh ngữ pháp của câu trước khi che. Với vai trò một Control Plane gọn nhẹ, mang tính đơn định (deterministic) và hiệu năng cao, việc sử dụng Regex cho các dữ liệu số/mẫu chuẩn hóa (Digits/Email) đảm bảo **Precision 100%** và **Độ trễ cực thấp (< 1ms)** mà không làm hỏng dữ liệu của khách hàng.

### 2. Quyết định không kiểm tra Luhn Checksum cho Số thẻ thanh toán
- **Hiện trạng**: Hệ thống che mọi chuỗi 13 đến 19 chữ số đứng riêng có định dạng thẻ mà **không** kiểm tra thuật toán Luhn (Mod 10 Checksum).
- **Lý do**: Khách hàng khi nhập lý do hoàn tiền thường có thể gõ nhầm 1 chữ số trong dãy 16 số của thẻ (ví dụ `4111 1111 1111 1112`). Nếu áp dụng kiểm tra Luhn checksum, chuỗi này sẽ bị coi là "thẻ không hợp lệ" và **bị bỏ qua không che**, dẫn đến việc gần như toàn bộ thông tin thẻ thật của khách hàng bị lọt thẳng ra AI bên ngoài (False Negative nghiêm trọng).
- **Vế đánh đổi (Trade-off)**: Khi không dùng Luhn, một số chuỗi 13-19 chữ số không phải thẻ (như mã vận đơn bưu chính dài) có thể bị che nhầm thành `[CARD_1]`. Chúng tôi chấp nhận đánh đổi này vì việc che nhầm mã vận đơn chỉ khiến AI mất một phần ngữ cảnh phụ, còn nếu lọt số thẻ thật thì vi phạm nghiêm trọng chuẩn an toàn dữ liệu tài chính (PCI-DSS).

### 3. PII dính liền chữ cái không bị che (Lý do phân biệt với quyết định không dùng Luhn)
- **Hiện trạng**: Do lookbehind `(?<![\w.+-])` và `(?<![\w-])` được thiết kế để bảo vệ các mã đơn hàng hoặc SKU có chứa số (`ORD-0912345678`, `SKU-4111111111111111`) không bị che nhầm, các chuỗi PII dính liền chữ cái như `Sdt0912345678` hay `CCCD001203004567` sẽ **không** bị che (đây là một Giới hạn đã biết - Known Limitation / False Negative).
- **Vì sao trường hợp này ưu tiên "không che nhầm" thay vì "che dư hơn che thiếu" như ở mục Luhn?**:
  - Mã đơn hàng (`orderId`) là dữ liệu ngữ cảnh nghiệp vụ cốt lõi mà AI và hệ thống policy nội bộ bắt buộc phải có để đối soát đơn hàng và phân tích lý do bồi hoàn; nếu mã đơn hàng bị che nhầm thành `[PHONE_1]` thì toàn bộ luồng phân tích sẽ bị sai lệch.
  - Ngược lại, số thẻ ngân hàng ở mục Luhn hoàn toàn không phải dữ liệu AI cần biết.
  - Do đó, việc bảo toàn tính chính xác của mã đơn hàng (`ORD-...`) được đặt mức ưu tiên cao hơn trường hợp hy hữu khách hàng viết dính liền không dấu cách (`Sdt0912...`). Trong phần lớn thực tế, khách hàng luôn viết có khoảng cách hoặc dấu phân cách (`SĐT: 0912...`, `CCCD: 0012...`) nên các PII hợp lệ vẫn được che đầy đủ.

### 4. Chữ số ngoài bảng mã ASCII (Non-ASCII Digits)
- **Hiện trạng**: Hệ thống đã xử lý chữ số full-width Unicode (`０-９`) về ASCII thông qua NFKC.
- **Giới hạn**: Ký tự `\d` của Java mặc định chỉ khớp `[0-9]` ASCII chuẩn. Các hệ chữ số khu vực khác (như chữ số Ả Rập - Indic: `٠, ١, ٢,...`) chưa được hỗ trợ che tự động. Đây là giới hạn được chấp nhận cho thị trường Việt Nam / Quốc tế dùng chữ số Latin.

### 5. Số điện thoại cố định (Số bàn) và Đầu số Hotline
- **Hiện trạng**: Quy tắc điện thoại tập trung vào dải số di động cá nhân phổ biến tại Việt Nam (các đầu số `03, 05, 07, 08, 09` và `+84`).
- **Giới hạn**: Chưa hỗ trợ số điện thoại bàn cố định (`028 3822 1234`) hoặc hotline doanh nghiệp (`1900 xxxx`, `1800 xxxx`) nhằm tránh che nhầm thông tin hotline hỗ trợ công khai của các đối tác vận chuyển hay cổng thanh toán.

### 6. Placeholder tự gõ trong văn bản gốc
- **Hiện trạng**: Nếu người dùng tự gõ `[CARD_1]` trong văn bản đầu vào, hệ thống không escape chuỗi này. Khi gọi hàm `restore()`, placeholder tự gõ có thể bị thay thế nếu trùng key trong mapping của chính request đó. Rủi ro này rất thấp vì bảng mapping được phân lập theo từng request và giải phóng ngay sau đó.

### 7. Email chứa chuỗi 16 số giữa các dấu chấm
- **Hiện trạng**: Do thứ tự ưu tiên xử lý là `CARD` $\rightarrow$ `EMAIL`, nếu một email chứa chuỗi 16 số đứng giữa dấu chấm (`a.4111111111111111.b@x.com`), phần số này sẽ bị thẻ nhận diện và che thành `[CARD_1]` trước khi regex email chạy qua. Đây là hệ quả chấp nhận được để đảm bảo số thẻ thanh toán luôn có mức ưu tiên bảo vệ cao nhất.

### 8. Tính chất chuẩn hóa của hàm Restore
- Hàm `restore()` khôi phục theo dạng **chuẩn hóa**, không bảo toàn từng ký tự gốc:
  - Nếu cùng một số xuất hiện ở 2 format khác nhau trong văn bản (ví dụ `4111 1111 1111 1111` và `4111-1111-1111-1111`), khi restore cả hai vị trí đều nhận format của lần xuất hiện đầu tiên.
  - Các chữ số full-width hoặc ký tự ẩn sau khi restore sẽ trở thành chữ số ASCII chuẩn.
