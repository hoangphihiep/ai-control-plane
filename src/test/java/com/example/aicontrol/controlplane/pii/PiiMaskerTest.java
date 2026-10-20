package com.example.aicontrol.controlplane.pii;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PiiMasker Unit Tests - Che và bảo vệ thông tin PII")
class PiiMaskerTest {

    private PiiMasker piiMasker;

    @BeforeEach
    void setUp() {
        piiMasker = new PiiMasker();
    }

    @Nested
    @DisplayName("1. Số thẻ thanh toán (CARD) - 13 đến 19 chữ số")
    class CardMaskingTests {

        @Test
        @DisplayName("Che thẻ 16 chữ số có khoảng trắng")
        void maskCard_WithSpaces() {
            String text = "Chuyển tiền vào thẻ 4111 1111 1111 1111 giúp tôi";
            MaskedResult result = piiMasker.mask(text);

            assertThat(result.maskedText()).isEqualTo("Chuyển tiền vào thẻ [CARD_1] giúp tôi");
            assertThat(result.mapping()).containsEntry("[CARD_1]", "4111 1111 1111 1111");
        }

        @Test
        @DisplayName("Che thẻ 16 chữ số có dấu gạch ngang")
        void maskCard_WithHyphens() {
            String text = "Số thẻ: 4111-1111-1111-1111";
            MaskedResult result = piiMasker.mask(text);

            assertThat(result.maskedText()).isEqualTo("Số thẻ: [CARD_1]");
            assertThat(result.mapping()).containsEntry("[CARD_1]", "4111-1111-1111-1111");
        }

        @Test
        @DisplayName("Che thẻ 16 chữ số viết liền")
        void maskCard_WithoutSeparators() {
            String text = "Thẻ 4111111111111111 bị lỗi thanh toán";
            MaskedResult result = piiMasker.mask(text);

            assertThat(result.maskedText()).isEqualTo("Thẻ [CARD_1] bị lỗi thanh toán");
            assertThat(result.mapping()).containsEntry("[CARD_1]", "4111111111111111");
        }

        @Test
        @DisplayName("Che thẻ 13 chữ số (Visa cũ) và 15 chữ số (Amex)")
        void maskCard_DifferentLengths() {
            String text = "Visa cũ: 4111111111111 và Amex: 378282246310005";
            MaskedResult result = piiMasker.mask(text);

            assertThat(result.maskedText()).isEqualTo("Visa cũ: [CARD_1] và Amex: [CARD_2]");
            assertThat(result.mapping())
                    .containsEntry("[CARD_1]", "4111111111111")
                    .containsEntry("[CARD_2]", "378282246310005");
        }

        @Test
        @DisplayName("Cùng số thẻ xuất hiện hai lần dùng chung [CARD_1]")
        void maskCard_DuplicateCardUsesSamePlaceholder() {
            String text = "Thẻ 4111 1111 1111 1111 hay 4111-1111-1111-1111 đều là một";
            MaskedResult result = piiMasker.mask(text);

            assertThat(result.maskedText()).isEqualTo("Thẻ [CARD_1] hay [CARD_1] đều là một");
            assertThat(result.mapping()).hasSize(1);
            assertThat(result.mapping()).containsKey("[CARD_1]");
        }
    }

    @Nested
    @DisplayName("2. Căn cước công dân (ID) - 12 chữ số")
    class IdMaskingTests {

        @Test
        @DisplayName("Che CCCD 12 chữ số viết liền")
        void maskId_WithoutSeparators() {
            String text = "Số CCCD của tôi là 001203004567";
            MaskedResult result = piiMasker.mask(text);

            assertThat(result.maskedText()).isEqualTo("Số CCCD của tôi là [ID_1]");
            assertThat(result.mapping()).containsEntry("[ID_1]", "001203004567");
        }

        @Test
        @DisplayName("Che CCCD 12 chữ số có khoảng trắng hoặc dấu gạch ngang")
        void maskId_WithSeparators() {
            String text = "CCCD 1: 001 203 004 567, CCCD 2: 079-203-004-890";
            MaskedResult result = piiMasker.mask(text);

            assertThat(result.maskedText()).isEqualTo("CCCD 1: [ID_1], CCCD 2: [ID_2]");
            assertThat(result.mapping())
                    .containsEntry("[ID_1]", "001 203 004 567")
                    .containsEntry("[ID_2]", "079-203-004-890");
        }

        @Test
        @DisplayName("Cùng CCCD xuất hiện nhiều lần dùng chung [ID_1]")
        void maskId_DuplicateUsesSamePlaceholder() {
            String text = "Xác nhận CCCD 001203004567, nhắc lại 001 203 004 567";
            MaskedResult result = piiMasker.mask(text);

            assertThat(result.maskedText()).isEqualTo("Xác nhận CCCD [ID_1], nhắc lại [ID_1]");
            assertThat(result.mapping()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("3. Email (EMAIL)")
    class EmailMaskingTests {

        @Test
        @DisplayName("Che email chuẩn và email tên miền Việt Nam")
        void maskEmail_StandardAndVietnamDomain() {
            String text = "Gửi qua email a.b+c@mail.co.vn hoặc support@shop.com";
            MaskedResult result = piiMasker.mask(text);

            assertThat(result.maskedText()).isEqualTo("Gửi qua email [EMAIL_1] hoặc [EMAIL_2]");
            assertThat(result.mapping())
                    .containsEntry("[EMAIL_1]", "a.b+c@mail.co.vn")
                    .containsEntry("[EMAIL_2]", "support@shop.com");
        }

        @Test
        @DisplayName("Che email ở cuối câu có dấu chấm kết thúc")
        void maskEmail_AtEndOfSentenceWithDot() {
            String text = "Vui lòng phản hồi qua a.b+c@mail.co.vn.";
            MaskedResult result = piiMasker.mask(text);

            assertThat(result.maskedText()).isEqualTo("Vui lòng phản hồi qua [EMAIL_1].");
            assertThat(result.mapping()).containsEntry("[EMAIL_1]", "a.b+c@mail.co.vn");
        }

        @Test
        @DisplayName("Cùng email khác chữ hoa/thường dùng chung [EMAIL_1]")
        void maskEmail_CaseInsensitiveReusesPlaceholder() {
            String text = "Liên hệ User@Mail.com hoặc user@mail.com";
            MaskedResult result = piiMasker.mask(text);

            assertThat(result.maskedText()).isEqualTo("Liên hệ [EMAIL_1] hoặc [EMAIL_1]");
            assertThat(result.mapping()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("4. Số điện thoại Việt Nam (PHONE)")
    class PhoneMaskingTests {

        @ParameterizedTest(name = "Che định dạng: {0}")
        @ValueSource(strings = {
                "0912345678",
                "0912 345 678",
                "0912.345.678",
                "+84 912 345 678",
                "(+84)912345678",
                "(+84) 912 345 678",
                "+84912345678",
                "0912-345-678"
        })
        @DisplayName("Che tất cả các định dạng số điện thoại Việt Nam phổ biến")
        void maskPhone_AllFormats(String phone) {
            String text = "Gọi cho tôi số " + phone + " nhé";
            MaskedResult result = piiMasker.mask(text);

            assertThat(result.maskedText()).isEqualTo("Gọi cho tôi số [PHONE_1] nhé");
            assertThat(result.mapping()).containsEntry("[PHONE_1]", phone);
        }

        @Test
        @DisplayName("Che các đầu số mạng di động Việt Nam (03, 05, 07, 08, 09)")
        void maskPhone_AllNetworkPrefixes() {
            String text = "Viettel: 0381234567, Vietnamobile: 0581234567, Mobi: 0781234567, Vina: 0881234567, Cũ: 0981234567";
            MaskedResult result = piiMasker.mask(text);

            assertThat(result.maskedText()).isEqualTo("Viettel: [PHONE_1], Vietnamobile: [PHONE_2], Mobi: [PHONE_3], Vina: [PHONE_4], Cũ: [PHONE_5]");
            assertThat(result.mapping()).hasSize(5);
        }

        @Test
        @DisplayName("Cùng số điện thoại xuất hiện nhiều lần ở các định dạng khác nhau vẫn dùng chung [PHONE_1]")
        void maskPhone_SamePhoneDifferentFormatsReusesPlaceholder() {
            String text = "SĐT 0912 345 678 hoặc 0912345678 hoặc +84 912 345 678";
            MaskedResult result = piiMasker.mask(text);

            assertThat(result.maskedText()).isEqualTo("SĐT [PHONE_1] hoặc [PHONE_1] hoặc [PHONE_1]");
            assertThat(result.mapping()).hasSize(1);
        }

        @Test
        @DisplayName("Hai số điện thoại khác nhau nhận [PHONE_1] và [PHONE_2]")
        void maskPhone_DistinctNumbersGetDistinctPlaceholders() {
            String text = "Số chính 0912345678, số phụ 0987654321";
            MaskedResult result = piiMasker.mask(text);

            assertThat(result.maskedText()).isEqualTo("Số chính [PHONE_1], số phụ [PHONE_2]");
            assertThat(result.mapping())
                    .containsEntry("[PHONE_1]", "0912345678")
                    .containsEntry("[PHONE_2]", "0987654321");
        }
    }

    @Nested
    @DisplayName("5. Thứ tự xử lý PII: Thẻ -> CCCD -> Email -> Điện thoại")
    class OrderOfExecutionTests {

        @Test
        @DisplayName("Số thẻ 16 chữ số chứa chuỗi giống SĐT không bị cắt vụn thành PHONE")
        void order_CardNotFragmentedByPhone() {
            // Chuỗi 16 số có phần đầu trông giống SĐT Việt Nam: 0912345678123456
            String text = "Số thẻ hoàn tiền: 0912345678123456";
            MaskedResult result = piiMasker.mask(text);

            assertThat(result.maskedText()).isEqualTo("Số thẻ hoàn tiền: [CARD_1]");
            assertThat(result.mapping()).containsEntry("[CARD_1]", "0912345678123456");
            assertThat(result.mapping()).doesNotContainKey("[PHONE_1]");
        }

        @Test
        @DisplayName("CCCD 12 chữ số có đầu số 079 (TP.HCM) không bị cắt thành PHONE")
        void order_IdNotFragmentedByPhone() {
            String text = "CCCD của khách: 079203004567";
            MaskedResult result = piiMasker.mask(text);

            assertThat(result.maskedText()).isEqualTo("CCCD của khách: [ID_1]");
            assertThat(result.mapping()).containsEntry("[ID_1]", "079203004567");
            assertThat(result.mapping()).doesNotContainKey("[PHONE_1]");
        }

        @Test
        @DisplayName("Email chứa chuỗi số điện thoại không bị tách rời chuỗi")
        void order_EmailContainingPhoneDigitsNotFragmented() {
            String text = "Email liên hệ: user0912345678@gmail.com";
            MaskedResult result = piiMasker.mask(text);

            assertThat(result.maskedText()).isEqualTo("Email liên hệ: [EMAIL_1]");
            assertThat(result.mapping()).containsEntry("[EMAIL_1]", "user0912345678@gmail.com");
            assertThat(result.mapping()).doesNotContainKey("[PHONE_1]");
        }

        @Test
        @DisplayName("Văn bản chứa đủ 4 loại PII theo thứ tự ngẫu nhiên")
        void order_AllFourTypesInSingleText() {
            String text = "Tôi dùng số 0912345678 mua hàng, thẻ 4111 1111 1111 1111, CCCD 001203004567, email contact@mail.com";
            MaskedResult result = piiMasker.mask(text);

            assertThat(result.maskedText())
                    .isEqualTo("Tôi dùng số [PHONE_1] mua hàng, thẻ [CARD_1], CCCD [ID_1], email [EMAIL_1]");
            assertThat(result.mapping())
                    .containsEntry("[CARD_1]", "4111 1111 1111 1111")
                    .containsEntry("[ID_1]", "001203004567")
                    .containsEntry("[EMAIL_1]", "contact@mail.com")
                    .containsEntry("[PHONE_1]", "0912345678");
        }
    }

    @Nested
    @DisplayName("6. Chuẩn hóa chữ số Full-Width (Unicode NFKC)")
    class FullWidthNormalizationTests {

        @Test
        @DisplayName("Che số điện thoại viết bằng chữ số full-width (０-９)")
        void maskFullWidth_PhoneNumber() {
            String text = "Liên hệ ０９１２３４５６７８ để nhận hàng";
            MaskedResult result = piiMasker.mask(text);

            assertThat(result.maskedText()).isEqualTo("Liên hệ [PHONE_1] để nhận hàng");
            assertThat(result.mapping()).containsEntry("[PHONE_1]", "0912345678");
        }

        @Test
        @DisplayName("Che số thẻ viết bằng chữ số full-width")
        void maskFullWidth_CardNumber() {
            String text = "Thẻ： ４１１１ １１１１ １１１１ １１１１";
            MaskedResult result = piiMasker.mask(text);

            assertThat(result.maskedText()).isEqualTo("Thẻ: [CARD_1]");
            assertThat(result.mapping()).containsEntry("[CARD_1]", "4111 1111 1111 1111");
        }

        @Test
        @DisplayName("Che CCCD viết bằng chữ số full-width")
        void maskFullWidth_IdNumber() {
            String text = "Số định danh: ００１２０３００４５６７";
            MaskedResult result = piiMasker.mask(text);

            assertThat(result.maskedText()).isEqualTo("Số định danh: [ID_1]");
            assertThat(result.mapping()).containsEntry("[ID_1]", "001203004567");
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "0912\u200B345678",
            "0912\u00AD345\u00AD678",
            "4111\u200B1111\u200B1111\u200B1111"
        })
        @DisplayName("Ký tự ẩn chen vào giữa chữ số vẫn phải bị che")
        void hiddenCharsInsideDigits_stillMasked(String pii) {
            MaskedResult r = piiMasker.mask("Liên hệ " + pii + " nhé");
            assertThat(r.mapping()).isNotEmpty();
            assertThat(r.maskedText()).doesNotContain("345").doesNotContain("1111");
        }
    }

    @Nested
    @DisplayName("7. Lookaround - Chỉ che chuỗi chữ số đứng riêng, không che một phần của chuỗi dài hơn")
    class StandaloneLookaroundTests {

        @ParameterizedTest(name = "Benign case không bị che: \"{0}\"")
        @ValueSource(strings = {
                "Đơn ORD-20261019",
                "Tôi bị trừ tiền 2 lần",
                "Số tiền 150000 đồng",
                "Giao ngày 19/10/2026",
                "Mã vận đơn 12345",
                "Hàng số 0912 bị lỗi"
        })
        @DisplayName("Ít nhất 6 trường hợp thông thường (benign) KHÔNG được che nhầm")
        void benignCases_MustNotBeMasked(String benignText) {
            MaskedResult result = piiMasker.mask(benignText);

            assertThat(result.maskedText())
                    .as("Văn bản thông thường không được bị che nhầm: %s", benignText)
                    .isEqualTo(benignText);
            assertThat(result.mapping()).isEmpty();
        }

        @Test
        @DisplayName("Chuỗi 20 chữ số viết liền không bị che thành thẻ")
        void standalone_20DigitsContinuousNotMasked() {
            String text = "Mã bưu kiện: 12345678901234567890 (20 chữ số)";
            MaskedResult result = piiMasker.mask(text);

            assertThat(result.maskedText()).isEqualTo(text);
            assertThat(result.mapping()).isEmpty();
        }

        @Test
        @DisplayName("Chuỗi 20 chữ số có khoảng trắng không bị cắt vụn 16 số làm thẻ")
        void standalone_20DigitsWithSpacesNotMasked() {
            String text = "Dãy số: 1234 5678 9012 3456 7890 không phải thẻ";
            MaskedResult result = piiMasker.mask(text);

            assertThat(result.maskedText()).isEqualTo(text);
            assertThat(result.mapping()).isEmpty();
        }

        @Test
        @DisplayName("Mã đơn hàng ORD-0912345678 không bị che thành SĐT")
        void standalone_OrderIdNotMaskedAsPhone() {
            String text = "Đơn hàng ORD-0912345678 bị hỏng bao bì";
            MaskedResult result = piiMasker.mask(text);

            assertThat(result.maskedText()).isEqualTo(text);
            assertThat(result.mapping()).isEmpty();
        }

        @Test
        @DisplayName("Chuỗi số dài hơn 10 chữ số nhưng không phải thẻ/CCCD (ví dụ 11 số bắt đầu bằng 1)")
        void standalone_NonMatchingNumbersRemainUntouched() {
            String text = "Mã SKU: 12345678901 và 999999999";
            MaskedResult result = piiMasker.mask(text);

            assertThat(result.maskedText()).isEqualTo(text);
            assertThat(result.mapping()).isEmpty();
        }

        @Test
        @DisplayName("Giới hạn đã biết (Known Limitation): Số điện thoại dính liền chữ cái (Sdt0912345678) bị lọt không che")
        void phoneGluedToLetters_isNotMasked_knownLimitation() {
            // GIỚI HẠN ĐÃ BIẾT (Known Limitation / False Negative):
            // Xem chi tiết giải trình tại README.md mục 3 ("PII dính liền chữ cái").
            // Lookbehind (?<![\w.+-]) và (?<![\w-]) được áp dụng để bảo vệ mã đơn hàng nội bộ (ORD-0912345678),
            // dẫn tới tác dụng phụ là chuỗi PII dính liền chữ cái như "Sdt0912345678" hay "CCCD001203004567" bị lọt qua.
            // Đây là đánh đổi có chủ đích để ưu tiên bảo toàn mã đơn hàng cho AI phân tích.
            String text = "Liên hệ Sdt0912345678 hoặc mã CCCD001203004567";
            MaskedResult r = piiMasker.mask(text);

            assertThat(r.maskedText()).isEqualTo(text);
            assertThat(r.mapping()).isEmpty();
        }
    }

    @Nested
    @DisplayName("8. Method phụ restore - Khôi phục văn bản gốc từ MaskedResult hoặc mapping")
    class RestoreTests {

        @Test
        @DisplayName("Khôi phục hoàn toàn văn bản có đầy đủ 4 loại PII: restore(mask(x)) == x")
        void restore_FullText() {
            String original = "Hoàn về thẻ 4111 1111 1111 1111, SĐT 0912345678, CCCD 001203004567, mail test@demo.vn";
            MaskedResult masked = piiMasker.mask(original);

            String restored = piiMasker.restore(masked);
            assertThat(restored).isEqualTo(original);

            // restore(mask(x)) trực tiếp trả về đúng x
            assertThat(piiMasker.restore(piiMasker.mask(original))).isEqualTo(original);
        }

        @Test
        @DisplayName("restore khôi phục theo dạng chuẩn hóa, không bảo toàn từng định dạng gốc nếu cùng số khác format")
        void restore_NormalizesFormatToFirstOccurrence() {
            String text = "Thẻ 4111 1111 1111 1111 hay 4111-1111-1111-1111";
            MaskedResult r = piiMasker.mask(text);

            // Cả hai vị trí đều nhận [CARD_1]
            assertThat(r.maskedText()).isEqualTo("Thẻ [CARD_1] hay [CARD_1]");

            String restored = piiMasker.restore(r);
            // Cả hai vị trí đều được khôi phục về dạng của lần xuất hiện đầu tiên ("4111 1111 1111 1111")
            assertThat(restored).isEqualTo("Thẻ 4111 1111 1111 1111 hay 4111 1111 1111 1111");
        }

        @Test
        @DisplayName("restore khôi phục chữ số full-width về dạng chuẩn ASCII đã được chuẩn hóa")
        void restore_FullWidthRestoredAsNormalizedAscii() {
            String text = "SĐT: ０９１２３４５６７８";
            MaskedResult r = piiMasker.mask(text);

            assertThat(r.maskedText()).isEqualTo("SĐT: [PHONE_1]");
            String restored = piiMasker.restore(r);
            assertThat(restored).isEqualTo("SĐT: 0912345678");
        }

        @Test
        @DisplayName("Khôi phục qua restore(maskedText, mapping)")
        void restore_WithExplicitMap() {
            String maskedText = "Gửi về [EMAIL_1] hoặc gọi [PHONE_1]";
            Map<String, String> mapping = Map.of(
                    "[EMAIL_1]", "admin@test.vn",
                    "[PHONE_1]", "0912345678"
            );

            String restored = piiMasker.restore(maskedText, mapping);
            assertThat(restored).isEqualTo("Gửi về admin@test.vn hoặc gọi 0912345678");
        }

        @Test
        @DisplayName("Xử lý an toàn khi đầu vào restore là null hoặc rỗng")
        void restore_SafeWithNullAndEmpty() {
            assertThat(piiMasker.restore((MaskedResult) null)).isNull();
            assertThat(piiMasker.restore(null, Map.of())).isNull();
            assertThat(piiMasker.restore("Văn bản không đổi", null)).isEqualTo("Văn bản không đổi");
            assertThat(piiMasker.restore("Văn bản không đổi", Map.of())).isEqualTo("Văn bản không đổi");
        }
    }

    @Nested
    @DisplayName("9. Bảo mật: MaskedResult.toString() không lộ dữ liệu PII")
    class SecurityToStringTests {

        @Test
        @DisplayName("toString() chỉ chứa maskedText và mappingCount, không chứa số thẻ, CCCD, phone, email")
        void toString_NeverLeaksPiiData() {
            String card = "4111 1111 1111 1111";
            String cccd = "001203004567";
            String phone = "0912345678";
            String email = "secret@vip.vn";

            String text = "Thẻ " + card + ", CCCD " + cccd + ", phone " + phone + ", mail " + email;
            MaskedResult result = piiMasker.mask(text);

            String stringRepr = result.toString();

            // Kiểm tra chỉ hiển thị placeholder và số lượng mapping
            assertThat(stringRepr).contains("[CARD_1]", "[ID_1]", "[PHONE_1]", "[EMAIL_1]");
            assertThat(stringRepr).contains("mappingCount=4");

            // Tuyệt đối không chứa bất kỳ giá trị thật nào qua vòng lặp
            List<String> rawPiiList = List.of(card, cccd, phone, email);
            for (String raw : rawPiiList) {
                assertThat(stringRepr)
                        .as("toString() không được chứa giá trị gốc: %s", raw)
                        .doesNotContain(raw);
            }
        }
    }

    @Nested
    @DisplayName("10. Xử lý đầu vào biên: null, chuỗi rỗng hoặc chỉ có khoảng trắng")
    class EdgeCasesTests {

        @Test
        @DisplayName("Trả về rỗng khi văn bản là null hoặc blank")
        void mask_NullOrBlank() {
            MaskedResult nullResult = piiMasker.mask(null);
            assertThat(nullResult.maskedText()).isNull();
            assertThat(nullResult.mapping()).isEmpty();

            MaskedResult emptyResult = piiMasker.mask("");
            assertThat(emptyResult.maskedText()).isEmpty();
            assertThat(emptyResult.mapping()).isEmpty();

            MaskedResult blankResult = piiMasker.mask("   ");
            assertThat(blankResult.maskedText()).isEqualTo("   ");
            assertThat(blankResult.mapping()).isEmpty();
        }

        @Test
        @DisplayName("Văn bản bình thường không chứa PII không bị thay đổi")
        void mask_NormalTextWithoutPii() {
            String normalText = "Hàng bị vỡ màn hình khi vận chuyển, yêu cầu đổi trả.";
            MaskedResult result = piiMasker.mask(normalText);

            assertThat(result.maskedText()).isEqualTo(normalText);
            assertThat(result.mapping()).isEmpty();
        }
    }

    @Nested
    @DisplayName("11. Test cốt lõi: Với câu chứa đủ 4 loại PII, maskedText tuyệt đối không chứa giá trị gốc")
    class CoreSecurityTests {

        @Test
        @DisplayName("Vòng lặp kiểm tra từng giá trị gốc không xuất hiện trong maskedText")
        void coreSecurityTest_MaskedTextContainsNoOriginalPiiValues() {
            String card = "4111 1111 1111 1111";
            String cccd = "001203004567";
            String email = "khachhang.a+b@mail.co.vn";
            String phone = "0912 345 678";

            String text = String.format(
                    "Yêu cầu hoàn tiền: thẻ %s, CCCD %s, liên hệ email %s hoặc SĐT %s ngay",
                    card, cccd, email, phone
            );

            MaskedResult result = piiMasker.mask(text);

            List<String> originalValues = List.of(card, cccd, email, phone);

            // Viết bằng vòng lặp qua từng giá trị gốc và assertThat(masked).doesNotContain(value)
            for (String value : originalValues) {
                assertThat(result.maskedText())
                        .as("maskedText tuyệt đối không được chứa giá trị gốc: %s", value)
                        .doesNotContain(value);
            }

            // Đảm bảo các placeholder tương ứng xuất hiện trong maskedText
            assertThat(result.maskedText()).contains("[CARD_1]", "[ID_1]", "[EMAIL_1]", "[PHONE_1]");
            assertThat(result.mapping()).hasSize(4);

            // restore(mask(x)) trả về đúng x
            assertThat(piiMasker.restore(result)).isEqualTo(text);
        }
    }

    @Nested
    @DisplayName("12. Kiểm tra quy tắc cùng giá trị/khác giá trị (Placeholder Deduplication)")
    class PlaceholderDeduplicationTests {

        @Test
        @DisplayName("Cùng giá trị xuất hiện hai lần → cùng placeholder; hai giá trị khác nhau → placeholder khác nhau")
        void deduplicationRules_SameValueReusesPlaceholder_DistinctValuesGetNewPlaceholder() {
            // Cùng giá trị xuất hiện 2 lần: cả hai đều là [PHONE_1]
            String samePhoneText = "SĐT 1 là 0912345678, liên hệ lại qua 0912345678 nhé";
            MaskedResult sameResult = piiMasker.mask(samePhoneText);
            assertThat(sameResult.maskedText()).isEqualTo("SĐT 1 là [PHONE_1], liên hệ lại qua [PHONE_1] nhé");
            assertThat(sameResult.mapping()).hasSize(1);
            assertThat(sameResult.mapping()).containsKey("[PHONE_1]");

            // Hai giá trị khác nhau: nhận [PHONE_1] và [PHONE_2]
            String distinctPhoneText = "SĐT chính 0912345678, SĐT phụ 0987654321";
            MaskedResult distinctResult = piiMasker.mask(distinctPhoneText);
            assertThat(distinctResult.maskedText()).isEqualTo("SĐT chính [PHONE_1], SĐT phụ [PHONE_2]");
            assertThat(distinctResult.mapping()).hasSize(2);
            assertThat(distinctResult.mapping())
                    .containsEntry("[PHONE_1]", "0912345678")
                    .containsEntry("[PHONE_2]", "0987654321");
        }
    }
}
