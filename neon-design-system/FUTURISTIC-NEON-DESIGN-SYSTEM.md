# Futuristic Neon Glass — Tài liệu phong cách thiết kế

> Phân tích từ hai ảnh tham chiếu `neon1.jpg` và `neon2.jpg`. Trọng tâm: nền không gian xanh tím, neon dịu, mặt kính có màu nhưng nhìn xuyên được, giao diện dạng hologram nhiều lớp.
>
> Đây là đặc tả phong cách thị giác. Các mã màu, kích thước, alpha, blur và chuyển động bên dưới là **giá trị đề xuất để tái tạo**, không phải thông số gốc được trích xuất từ ảnh. Hai ảnh là minh họa tĩnh; không xác định được font, CSS, hành vi tương tác hoặc cấu trúc sản phẩm gốc.
>
> **Thực thi:** phong cách này đã được triển khai thành bộ component **Prism · Neon Design System** trong `neon-design-system/` (v0.8.0, 56 component Vue 3, tokens `--ds-*` ba lớp). Mục 4, 5, 9, 10 dưới đây đã được đồng bộ với giá trị thực tế của `neon-design-system/src/tokens.css` + `components.css`; mục 15 mô tả kiến trúc và danh mục component thực tế. Khi giá trị trong tài liệu này và mã nguồn khác nhau, **mã nguồn là nguồn chính**.

## 1. Bản sắc tổng thể

Tên mô tả: **Futuristic Neon Glass / Holographic Interface**.

Cảm giác chủ đạo là công nghệ tương lai, nhẹ, phát sáng và có chiều sâu. Các cửa sổ giống những tấm kính màu lơ lửng trong không gian tối. Người xem nhận biết hình khối nhờ đường viền và các nét cấu trúc sáng, trong khi phần ruột vẫn hòa vào nền.

Công thức thị giác:

**Nền indigo/navy sâu + ánh sáng cyan/violet cục bộ + mặt kính alpha thấp + viền mảnh sắc + quầng sáng mềm + nét wireframe.**

Ba đặc tính cần giữ khi chuyển thành UI:

1. **Xuyên thấu:** nhận ra được mảng màu, đường nét hoặc vật thể phía sau component.
2. **Phát sáng có phân cấp:** viền, icon và điểm tương tác sáng hơn mặt kính; chữ vẫn sắc nét.
3. **Có lớp không gian:** nền, ánh sáng, kính, nội dung và chi tiết nổi tạo thành các tầng khác nhau.

## 2. Phân tích từng ảnh

### 2.1. Ảnh 1 — kính mềm và mạng kết nối

![Ảnh 1: cửa sổ kính giữa các biểu tượng công nghệ](./neon1.jpg)

| Thành phần quan sát | Đặc điểm | Ý nghĩa khi tái tạo |
| --- | --- | --- |
| Nền | Navy pha tím đậm; vùng xanh sáng nhẹ gần cụm trung tâm | Dùng gradient rộng, tránh nền đen phẳng |
| Cửa sổ trung tâm | Mặt xanh tím bán trong, viền nhạt, góc bo nhỏ | Kính là một lớp màu mỏng, có khung rõ |
| Thanh tiêu đề | Sáng hơn phần thân, có đường phân cách và chấm nhỏ | Tạo cảm giác mép kính bắt sáng |
| Mặt kính | Có vùng sáng chéo và sắc độ thay đổi | Thêm phản xạ nhẹ, không phủ trắng toàn bộ |
| Nội dung cửa sổ | Khung chữ nhật, đường chéo, thanh ngắn và node | Ngôn ngữ wireframe, ít khối đặc |
| Mũi tên | Cyan, mint, tím; nét cong, chuyển màu và có vệt sáng | Biểu diễn luồng dữ liệu bằng đường có hướng |
| Icon xung quanh | AI dạng mạng điểm; VR, microphone và khối blockchain dạng nét | Dùng icon outline có cấu trúc hình học |
| Họa tiết phụ | Các cụm chấm vuông mờ và khung nét đứt lệch lớp | Gợi không gian số, chỉ dùng làm nền phụ |
| Typography | Tiêu đề trắng đậm, nhãn trắng đơn giản | Nội dung chính cần độ tương phản rõ, ít glow |

Cửa sổ có độ hiện diện nhờ lớp màu nhẹ, mép sáng và phản xạ. Không có cảm giác một chiếc card đặc đặt lên nền. Khung nét đứt phía dưới bên phải cùng các họa tiết mờ tạo cảm giác những mặt phẳng nằm ở độ sâu khác nhau.

### 2.2. Ảnh 2 — hologram cyan và thiết bị nổi

![Ảnh 2: cụm giao diện thiết bị chiếu nổi](./neon2.jpg)

| Thành phần quan sát | Đặc điểm | Ý nghĩa khi tái tạo |
| --- | --- | --- |
| Nền | Indigo/tím sáng hơn phía trên, tối dần phía dưới | Có thể dùng nền sáng hơn ảnh 1 nhưng vẫn sâu |
| Khung trình duyệt | Viền cyan sáng, có đường viền lệch phía sau | Dùng viền đôi nhẹ để gợi độ dày và dư ảnh |
| Điện thoại và đồng hồ | Xoay nhẹ, chồng lên cửa sổ lớn | Tạo chiều sâu bằng overlap và góc nghiêng trong minh họa |
| Mặt thiết bị | Nhuộm xanh, vẫn hòa vào vùng chiếu và lớp phía sau | Giữ alpha thấp ở phần minh họa hologram |
| Chi tiết UI | Nét cyan mảnh, một vài ô tím/lavender đặc hơn | Accent chỉ chiếm diện tích nhỏ |
| Vùng chiếu sáng | Mảng xanh dạng hình thang tỏa lên từ bệ | Nguồn sáng có phương hướng, không rải glow đều |
| Đường kết nối | Nét cong mảnh, điểm cuối sáng | Có nhịp dẫn mắt từ bệ lên thiết bị |
| Bệ đỡ | Khối xanh tím đặc, các mặt có độ sáng khác nhau | Vật thể đặc làm đối trọng cho phần hologram nhẹ |
| Biểu tượng chân bệ | Nét đậm hơn, một số mảng màu phẳng | Ngôn ngữ minh họa có sự pha trộn; không áp dụng mọi nét đậm vào UI |

Ảnh 2 cho cảm giác phát sáng mạnh hơn ảnh 1 vì đường cyan tập trung trên nền tím và có vùng ánh sáng bên dưới. Cảm giác kính xuất phát từ sự kết hợp của mặt màu mỏng, viền sáng và chồng lớp; không đủ căn cứ để khẳng định ảnh dùng hiệu ứng blur thực tế.

### 2.3. Phần chung và cách kết hợp

| Thuộc tính | Ảnh 1 | Ảnh 2 | Quy tắc kết hợp |
| --- | --- | --- | --- |
| Màu ánh sáng | Cyan, mint, lavender | Cyan/turquoise chủ đạo, tím phụ | Cyan là chính, violet là phụ |
| Bề mặt | Kính mềm, hơi sương | Kính chiếu nổi, nét rõ | Surface mềm, đường cấu trúc sắc |
| Glow | Dịu và lan rộng | Rõ hơn quanh khung | Glow mạnh tại vùng cần chú ý |
| Chiều sâu | Khung phụ, phản xạ, lớp mờ | Chồng thiết bị, viền đôi, vùng chiếu | Ưu tiên overlap và ánh sáng trước shadow lớn |
| Bố cục | Trung tâm và các nhánh kết nối | Cụm thiết bị nổi trên bệ | Có một vùng trọng tâm rõ |

Viền đen bên ngoài hai ảnh là phần bao quanh hình tham chiếu, không cần đưa vào canvas sản phẩm. Các ô có đường chéo là ký hiệu placeholder trong minh họa, không phải yêu cầu dùng cho ảnh nội dung thật.

## 3. Nền: điều kiện để nhìn thấy kính

Nền phải có biến thiên vừa đủ. Một lớp kính alpha thấp đặt trên nền màu phẳng sẽ khó tạo cảm giác xuyên thấu, vì không có dấu hiệu thị giác để nhận ra phần phía sau.

Cấu trúc nền đề xuất từ xa đến gần:

1. **Base:** navy/indigo tối, phủ toàn màn hình.
2. **Ambient:** radial gradient cyan lệch trái hoặc sau vùng trọng tâm, violet ở phía đối diện.
3. **Chi tiết xa:** vài đường cong, cụm chấm hoặc wireframe mờ.
4. **Ánh sáng định hướng:** vùng sáng phía sau hoặc dưới cụm chính khi cần chất hologram ảnh 2.
5. **Component kính:** nhìn xuyên được các lớp trên.

Họa tiết nền chỉ nên có alpha khoảng 3–8%; ánh sáng môi trường khoảng 8–18% tùy màu. Không phủ toàn màn hình bằng lưới sáng, hạt dày hoặc tia neon. Hai ảnh có nhiều khoảng tối giúp những nét sáng trở nên nổi bật.

## 4. Bảng màu

Các HEX dưới đây là **token thực tế** của `neon-design-system/src/tokens.css` (khớp cảm nhận thị giác từ ảnh; tên token dùng namespace `--ds-*`).

### 4.1. Palette gốc (global tokens)

| Token | Giá trị | Vai trò |
| --- | --- | --- |
| `--ds-color-space-950` | `#100d2b` | Nền sâu (`--space-deep` ở bản phác thảo) |
| `--ds-color-space-900` | `#101b3e` | Vùng nền xanh ảnh 1 |
| `--ds-color-indigo-700` | `#302577` | Vùng tím sáng ảnh 2 |
| `--ds-color-cyan-400` | `#67e8f9` | Viền chính, focus, tín hiệu tương tác |
| `--ds-color-teal-400` | `#49dccf` | Đường nối, ánh sáng phụ |
| `--ds-color-violet-400` | `#a58aef` | Accent tím, điểm nhấn phụ |
| `--ds-color-mint-300` | `#9ce8d0` | Accent mềm, tín hiệu tích cực |
| `--ds-color-rose-300` | `#ffacc4` | Màu lỗi/danger (bổ sung khi triển khai) |
| `--ds-color-amber-300` | `#f3d28c` | Màu cảnh báo/warning (bổ sung khi triển khai) |

### 4.2. Semantic tokens (lớp 2)

| Token | Giá trị | Vai trò |
| --- | --- | --- |
| `--ds-color-canvas` | `var(--ds-color-space-950)` | Nền ứng dụng |
| `--ds-color-text-primary` | `#eef8ff` | Nội dung chính |
| `--ds-color-text-secondary` | `#bacce4` | Nội dung phụ |
| `--ds-color-text-muted` | `#99acc9` | Metadata, cần kiểm tra trên nền thật |
| `--ds-color-accent` | `var(--ds-color-cyan-400)` | Accent chính |
| `--ds-color-success` | `var(--ds-color-mint-300)` | Thành công |
| `--ds-color-danger` | `var(--ds-color-rose-300)` | Lỗi |
| `--ds-color-warning` | `var(--ds-color-amber-300)` | Cảnh báo |
| `--ds-color-border` | `rgb(133 234 255 / .17)` | Viền nghỉ |
| `--ds-color-border-strong` | `rgb(133 234 255 / .42)` | Viền hover/focus |

Tỷ lệ diện tích tham khảo: khoảng 75% nền tối, 18% mặt kính và ánh sáng mềm, 7% nét sáng và accent. Đây là hướng cân bằng thị giác, không phải tỷ lệ đo từ ảnh.

Cyan và violet là màu nhận diện. Hệ semantic đã đầy đủ trong tokens (success/danger/warning như bảng 4.2) — ảnh gốc không cung cấp, phần này là bổ sung khi triển khai. Không mặc định mọi chi tiết tím là lỗi hoặc mọi chi tiết cyan đều có thể bấm.

## 5. Vật liệu kính xuyên thấu

### 5.1. Cấu tạo một tấm kính

| Lớp | Cách thể hiện | Mục đích |
| --- | --- | --- |
| Nền phía sau | Gradient, nét trang trí hoặc vật thể thật phía sau | Cung cấp dấu hiệu để thấy xuyên qua |
| Tint | Màu navy/cyan alpha thấp | Xác định mặt kính mà vẫn giữ nền |
| Phản xạ | Gradient trắng/cyan rất nhẹ, lệch góc | Gợi ánh sáng lướt trên bề mặt |
| Viền | Nét 1px có độ sáng rõ hơn tint | Xác định hình học |
| Mép trong | Inset highlight nhỏ | Gợi độ dày của kính |
| Glow ngoài | Cyan lan mềm, alpha thấp | Gợi khung phát sáng |
| Nội dung | Chữ và icon sắc, độc lập với alpha của kính | Giữ khả năng đọc |

**Chỉ làm trong nền component, không giảm `opacity` của cả component.** Giảm opacity ở phần tử cha làm chữ, icon, viền và con bên trong mờ theo.

### 5.2. Mức độ trong đề xuất

Giá trị **mặc định đã triển khai** trong `tokens.css`: surface `--ds-surface-alpha: .10` + `--ds-surface-blur: 4px`; dialog `--ds-dialog-background: rgb(15 32 62 / .40)` + `--ds-dialog-blur: 7px`. Bảng dưới là dải khuyến nghị theo loại surface; cột "Đã dùng" là giá trị token hiện tại.

| Loại surface | Alpha lớp tint | Blur khởi điểm | Đã dùng (token) | Cách dùng |
| --- | --- | --- | --- | --- |
| Hologram minh họa | 0.08–0.16 | 0–3px | — | Khung thiết bị, hình trang trí |
| Card kính | 0.08–0.26 | 3–6px | `--ds-surface-alpha: .10` / `--ds-surface-blur: 4px` | Nội dung ngắn, dashboard gọn |
| Popover/menu | 0.28–0.40 | 5–8px | dùng chung surface token | Lựa chọn, thao tác ngắn |
| Dialog/popup | 0.30–0.46 | 6–10px | `--ds-dialog-background` (.40) / `--ds-dialog-blur: 7px` | Nội dung cần tập trung |
| Vùng đọc dày đặc | 0.50–0.68 | 6–12px | tăng cục bộ | Chỉ tăng cục bộ sau nội dung dài |

Alpha trong bảng chỉ áp dụng cho lớp tint chính. Gradient phản xạ, nền lồng nhau và scrim đều làm kết quả tối hoặc đục hơn. Hai lớp tint alpha 0.30 chồng nhau có độ che tổng tương đương `1 - (1 - 0.30)^2 = 0.51`, nếu xét riêng mức che phủ; màu đầu ra còn phụ thuộc từng lớp.

Glass trong Prism được điều chỉnh qua token (không sửa từng component): override `--ds-surface-alpha` / `--ds-surface-blur` sau stylesheet. Demo AI Skill Management có **Theme Lab** (Settings) chỉnh trực tiếp "Độ che mặt kính" 0–65% và "Backdrop blur" 0–16px, mặc định 10% / 4px, lưu `localStorage`.

### 5.3. Trong suốt và kính mờ

Yêu cầu ưu tiên của style này là **nhìn xuyên được background**. Blur chỉ hỗ trợ tách nội dung khỏi nhiễu nền. Nếu blur lớn đến mức đường nét phía sau biến mất hoàn toàn, bề mặt sẽ chuyển sang kính mờ dày và xa cảm giác hologram của ảnh.

- Bắt đầu với blur thấp; tăng vừa đủ theo độ phức tạp của nền.
- Khi chữ khó đọc, trước tiên giảm chi tiết nền ngay sau chữ hoặc tăng tint cục bộ.
- Không phủ lớp trắng alpha lớn: dễ tạo cảm giác nhựa sữa.
- Không dùng một gradient hoàn toàn opaque rồi đặt `backdrop-filter` lên trên và kỳ vọng thấy nền.
- Để kiểm tra độ trong, đặt một đường cong hoặc mảng sáng đi qua cả phía ngoài và phía sau kính: phải nhận ra tính liên tục của nó.

## 6. Neon: lõi sắc, quầng mềm

Neon không chỉ là `box-shadow`. Hiệu ứng cần một nét lõi có hình dạng rõ và một quầng sáng hỗ trợ xung quanh.

1. **Lõi:** stroke/border khoảng 1px cho panel, 1.5–2px cho icon ở kích thước thông thường.
2. **Quầng gần:** blur 4–8px, alpha 0.15–0.30.
3. **Quầng xa:** blur 16–28px, alpha 0.06–0.14.
4. **Ánh sáng môi trường:** mảng sáng lớn sau một cụm, không gắn lên từng phần tử.

| Cấp | Áp dụng | Cường độ |
| --- | --- | --- |
| 0 | Body text, divider, nội dung thông thường | Không glow |
| 1 | Card, input nghỉ, icon phụ | Viền sáng nhẹ, quầng gần nhỏ |
| 2 | Hover, selected, popup chính | Viền rõ, thêm quầng xa nhẹ |
| 3 | Hero, điểm kích hoạt quan trọng trong minh họa | Sáng nhất nhưng diện tích nhỏ |

Tránh glow mạnh trên cả chữ, tất cả cạnh và mọi control cùng lúc. Nếu quầng sáng làm mất mép hình học, giảm alpha hoặc bán kính. Violet dùng ở điểm nhấn, không biến toàn bộ hệ thành neon hồng rực.

## 7. Popup, modal và các lớp overlay

Hai ảnh không thể hiện modal tương tác thực tế. Đặc tả dưới đây là cách chuyển ngôn ngữ kính của cửa sổ minh họa sang popup theo yêu cầu.

### 7.1. Anatomy đề xuất

- Scrim navy nhẹ phủ màn hình, alpha khởi điểm 0.24–0.34.
- Mặt popup navy/cyan alpha khoảng 0.36, vẫn nhận ra nền phía sau.
- Viền cyan 1px, highlight phía trên và cạnh đón sáng.
- Quầng sáng mềm quanh khung; shadow tối nhẹ để tách khỏi lớp gần.
- Header chỉ sáng hơn thân một chút, có divider mảnh.
- Body không lồng thêm nhiều card kính; dùng khoảng cách để chia nhóm.
- Footer có nền cùng vật liệu, các action giữ chữ rõ.

Không làm scrim quá đen hoặc blur toàn bộ backdrop quá mạnh: kính có thể vẫn trong về kỹ thuật nhưng người xem không còn thấy gì phía sau. Popover/menu thông thường không cần scrim.

### 7.2. Nguyên tắc chiều sâu

Thứ tự lớp: canvas → chi tiết nền → nội dung trang → scrim modal → popup → tooltip bên trong popup khi cần. Với native dialog, tận dụng top layer và `::backdrop`; không cố giải quyết mọi overlay bằng các giá trị z-index cực lớn.

Tránh phủ nhiều vùng `backdrop-filter` lớn lên nhau. Nested glass làm tăng độ đục và chi phí render. Popup cần quản lý focus, thao tác đóng và khôi phục focus; độ trong suốt không làm thay đổi các yêu cầu tương tác đó.

### 7.3. Panel nổi (popover, menu, combobox, color picker)

Panel nổi neo vào trigger phải **teleport ra `body` + `position: fixed`** và tính `top`/`left` từ `getBoundingClientRect()` của trigger. Lý do:

- `backdrop-filter` (vật liệu kính) tạo stacking context mới cho phần tử mang nó — z-index của panel đặt *bên trong* card kính không thể vượt ra ngoài card đó, nên panel luôn bị che bởi các card sibling.
- `overflow: hidden` của container (card specimen, table, dialog body) cắt panel theo box của nó.
- Teleport ra `body` + `position: fixed` thoát khỏi cả hai; z-index 85 (panel) / 90 (date popup, tooltip) là thang dùng chung.

Quy tắc bắt buộc khi viết CSS cho panel:

1. Class panel **phải khai báo `position: fixed`** trong stylesheet. Chỉ đặt `top`/`left` inline mà thiếu `position: fixed` sẽ khiến panel rơi vào flow thường cuối `<body>` — nằm dưới chân trang, ngoài màn hình, không thao tác được (lỗi đã xảy ra thực tế với 4 panel: combobox, dropdown menu, color picker, popover).
2. Panel phải tự kẹp trong viewport (lật lên khi gần đáy, kẹp ngang khi gần mép phải) và recalibrate khi resize/scroll.
3. Đóng khi click ngoài hoặc Escape; click bên trong panel không đóng.
4. Không dùng z-index cực lớn để "thắng" stacking context — đó là dấu hiệu panel đang bị đặt sai vị trí trong DOM thay vì teleport.

## 8. Áp dụng vào component

| Component | Bề mặt và viền | Nội dung/trạng thái |
| --- | --- | --- |
| Card | Tint thấp, border cyan dịu, radius 10–14px | Tiêu đề rõ, không glow cả đoạn văn |
| Primary button | Cyan tint cao hơn card, viền sáng | Chữ gần trắng; hover tăng viền và tint nhẹ |
| Secondary button | Kính tối hoặc violet tint nhẹ | Accent tím, glow thấp hơn primary |
| Ghost button | Nền trong, viền tối thiểu | Hover xuất hiện mặt màu nhẹ |
| Input/select | Navy trong, viền cyan thấp | Focus có outline rõ; placeholder không mờ quá mức |
| Tabs | Container nhẹ, ít khung | Active có underline cyan và tint nhỏ |
| Toggle/checkbox | Track tối, phần chọn cyan | Kết hợp hình dạng/check với màu |
| Menu/popover | Tint cao hơn card, blur vừa | Hover từng dòng bằng tint, tránh glow từng item |
| Tooltip | Nền tối rõ hơn, border nhỏ | Ưu tiên đọc nhanh, không ép alpha thấp |
| Toast | Kính vừa, accent ở cạnh/icon | Có text mô tả trạng thái |
| Table | Một surface chung, divider mờ | Tránh mỗi ô là một hộp neon |
| Chart | Nét cyan/violet, grid cực mờ | Glow hạn chế, label sắc |
| Skeleton | Mảng xanh tím mờ | Chuyển động nhẹ nếu cần |

State đề xuất: default ổn định; hover tăng sáng nhẹ; active giảm glow và tăng tint chút ít; focus-visible có outline riêng; disabled giảm độ nổi của nền/viền nhưng vẫn đọc được nhãn. Selected cần dấu hiệu bền vững như underline, check hoặc nhãn; không chỉ khác một chút glow.

## 9. Hình học, typography và bố cục

### Hình học

- Chủ đạo là hình chữ nhật bo nhẹ, nét mảnh, không phải mọi thứ đều dạng pill.
- Radius gợi ý: control 8px, card 12px, dialog 16px; điều chỉnh theo kích thước.
- Viền đôi chỉ dùng cho khung nổi bật; khoảng lệch 3–6px và giảm sáng lớp sau.
- Đường kết nối cong mượt, đầu node là chấm sáng nhỏ.
- Các góc nghiêng của thiết bị thuộc minh họa; form và dữ liệu thật nên giữ thẳng để dễ đọc.

### Typography

Ảnh 1 dùng sans-serif đậm cho heading và sans-serif đơn giản cho nhãn. Ảnh không đủ chi tiết để xác định họ font. Prism dùng hai token font (trong `tokens.css`):

- `--ds-font-sans`: `'Segoe UI', system-ui, -apple-system, sans-serif` — chữ chính, hỗ trợ tiếng Việt.
- `--ds-font-mono`: `'Cascadia Code', 'SFMono-Regular', Consolas, monospace` — số liệu KPI, code, nhãn kỹ thuật.

| Vai trò | Cỡ chữ gợi ý | Weight | Line-height |
| --- | --- | --- | --- |
| Display | 32–48px | 650–750 | 1.15–1.25 |
| Page heading | 24–32px | 600–700 | 1.25 |
| Dialog heading | 20–24px | 600–700 | 1.3 |
| Body | 14–16px | 400–500 | 1.5–1.65 |
| Label | 13–14px | 500–600 | 1.4 |
| Metadata | 12–13px | 400–500 | 1.45 |

Uppercase phù hợp tiêu đề ngắn như ảnh 1; không dùng cho đoạn văn. Không mô phỏng độ nhòe/nén của JPG vào chữ UI. Chữ trong ứng dụng phải sắc hơn chi tiết trong ảnh tham chiếu.

### Spacing và responsive

Scale spacing thực tế trong `tokens.css`: `--ds-space-1…16` = 4, 8, 12, 16, 20, 24, 32, 48, 64px. Radius: `--ds-radius-sm` 8px, `--ds-radius-md` 12px, `--ds-radius-lg` 16px. `--ds-control-height` mặc định 44px (demo AI Skill Management override xuống 40px cho compact). Card padding 20–24px; dialog 24–32px trên desktop, 16–20px trên mobile. Giữ khoảng trống quanh vùng sáng để tạo độ sâu.

Trên màn hình nhỏ, giảm số lớp trang trí, thu gọn glow và chuyển bố cục thành một cột. Popup có lề tối thiểu 16px, nội dung dài cuộn trong vùng hợp lý. Không thu nhỏ toàn bộ minh họa desktop để làm UI mobile.

## 10. CSS tham khảo để tái tạo vật liệu

**Thực tế:** vật liệu đã được triển khai trong `neon-design-system/src/tokens.css` (token) + `components.css` (class `.p-glass` cho mọi bề mặt kính, `.p-button`, `.p-input-wrap`, `.p-date-popup`, …). Ví dụ dưới đây là bản rút gọn dùng **tên token thật** (`--ds-*`) để đối chiếu; khi viết CSS mới, ưu tiên dùng token thay vì hardcode.

```css
/* tokens.css (rút gọn — bản đầy đủ xem neon-design-system/src/tokens.css) */
:root {
  color-scheme: dark;
  /* Global */
  --ds-color-space-950: #100d2b;
  --ds-color-space-900: #101b3e;
  --ds-color-indigo-700: #302577;
  --ds-color-cyan-400: #67e8f9;
  --ds-color-violet-400: #a58aef;
  --ds-color-text-primary: #eef8ff;
  --ds-color-text-secondary: #bacce4;
  --ds-radius-sm: 8px;
  --ds-radius-md: 12px;
  --ds-radius-lg: 16px;
  --ds-duration-fast: 160ms;
  --ds-duration-normal: 220ms;
  --ds-ease: cubic-bezier(.33, 1, .68, 1);
  /* Semantic — vật liệu kính */
  --ds-color-accent: var(--ds-color-cyan-400);
  --ds-color-border: rgb(133 234 255 / .17);
  --ds-color-border-strong: rgb(133 234 255 / .42);
  --ds-surface-alpha: .10;
  --ds-surface-blur: 4px;
  --ds-surface-tint: rgb(24 55 92 / var(--ds-surface-alpha));
  --ds-surface-reflection: linear-gradient(125deg,
    rgb(181 241 255 / .055), rgb(181 241 255 / .005) 42%, rgb(165 138 239 / .035));
  --ds-shadow-glass: inset 0 1px 0 rgb(219 252 255 / .09),
    0 0 8px rgb(103 232 249 / .04), 0 12px 36px rgb(3 5 23 / .14);
  --ds-shadow-glow: 0 0 6px rgb(103 232 249 / .16), 0 0 26px rgb(103 232 249 / .08);
  --ds-focus-ring: 2px solid var(--ds-color-accent);
  --ds-control-height: 44px;
  /* Component */
  --ds-button-primary-background: rgb(39 151 180 / .20);
  --ds-button-secondary-background: rgb(126 94 211 / .16);
  --ds-input-background: rgb(9 20 45 / .35);
  --ds-dialog-background: rgb(15 32 62 / .40);
  --ds-dialog-blur: 7px;
  --ds-dialog-scrim: rgb(5 8 26 / .30);
}

/* components.css — một class duy nhất cho mọi bề mặt kính */
.p-glass {
  background: var(--ds-surface-reflection), var(--ds-surface-tint);
  border: 1px solid var(--ds-color-border);
  border-radius: var(--ds-radius-md);
  -webkit-backdrop-filter: blur(var(--ds-surface-blur)) saturate(115%);
  backdrop-filter: blur(var(--ds-surface-blur)) saturate(115%);
  box-shadow: var(--ds-shadow-glass);
}

/* Nền không gian: dùng component PrismAmbientBackground
   (5 radial glow + orb + plexus + bokeh + dot grid + noise + vignette,
   aria-hidden, pointer-events: none, z-index 0) — không tự viết gradient. */

/* Dialog: token riêng để dễ đọc (alpha 40%, blur 7px, scrim navy 30%) */
.p-dialog {
  --ds-surface-tint: var(--ds-dialog-background);
  backdrop-filter: blur(var(--ds-dialog-blur)) saturate(115%);
  border-radius: var(--ds-radius-lg);
  width: min(560px, calc(100vw - 32px));
  max-height: calc(100svh - 32px);
  overflow: auto;
}
.p-dialog::backdrop {
  background: var(--ds-dialog-scrim);
  /* Giữ chi tiết phía sau để thấy xuyên qua mặt dialog. */
}

/* Button (rút gọn từ .p-button trong components.css) */
.p-button {
  min-height: var(--ds-control-height);
  padding: 10px 18px;
  border: 1px solid var(--ds-color-border-strong);
  border-radius: var(--ds-radius-sm);
  background: var(--ds-button-primary-background);
  color: var(--ds-color-text-primary);
  font: inherit;
  cursor: pointer;
  transition: background var(--ds-duration-fast) var(--ds-ease),
    box-shadow var(--ds-duration-fast) var(--ds-ease),
    border-color var(--ds-duration-fast) var(--ds-ease);
}
@media (hover: hover) {
  .p-button:hover:not(:disabled) {
    background: rgb(103 232 249 / .14);
    border-color: var(--ds-color-border-strong);
    box-shadow: var(--ds-shadow-glow);
  }
}
.p-button:focus-visible {
  outline: var(--ds-focus-ring);
  outline-offset: 4px;
}
.p-button:active:not(:disabled) {
  background: rgb(103 232 249 / .23);
  box-shadow: none;
}
.p-button:disabled {
  color: var(--ds-color-text-muted);
  border-color: var(--ds-color-border);
  background: rgb(24 55 92 / .12);
  cursor: not-allowed;
}

@supports not ((backdrop-filter: blur(1px)) or
               (-webkit-backdrop-filter: blur(1px))) {
  /* Fallback: tăng tint khi không có blur; vẫn chừa độ trong. */
  :root { --ds-surface-alpha: .55; }
}

@media (prefers-reduced-motion: reduce) {
  * { transition: none !important; animation: none !important; }
}
```

`backdrop-filter` xử lý phần ảnh phía sau bề mặt; `filter: blur()` làm mờ chính phần tử và nội dung của nó. Cần thử nghiệm trong cấu trúc trang thật vì ancestor, clipping và các lớp compositing có thể ảnh hưởng hiệu ứng. Không thêm `opacity`, `filter` hay `will-change` tùy tiện lên ancestor của kính.

**Override theo theme:** đặt sau stylesheet, ví dụ:

```css
:root {
  --ds-surface-alpha: .24;
  --ds-surface-blur: 4px;
  --ds-dialog-background: rgb(15 32 62 / .40);
}
```

Demo AI Skill Management có 2 theme (`prism` 10%/4px và `aipro` 7%/8px) + Theme Lab chỉnh realtime (mục 5.2).

Để tạo biến thể gần ảnh 2, tăng nền indigo, giảm blur xuống 2–4px, tăng sáng border nhẹ và thêm một vùng chiếu cyan sau cụm chính. Để gần ảnh 1, giữ nền tối hơn, dùng viền nhạt hơn và phản xạ xanh tím dịu.

## 11. Chuyển động và ánh sáng động

Ảnh không cung cấp bằng chứng về animation. Các gợi ý này là phần mở rộng cho sản phẩm:

- Hover/focus chuyển nhẹ trong 140–180ms.
- Popup vào bằng dịch chuyển 4–8px và fade ngắn 180–240ms.
- Chỉ minh họa hero mới cần điểm sáng chạy trên đường nối hoặc chuyển động nổi rất chậm.
- Không pulse liên tục toàn bộ viền card, input và popup.
- Không animate blur hoặc shadow lớn trên nhiều phần tử cùng lúc.
- Reduced motion bỏ chuyển động trang trí; giữ trạng thái rõ ràng ở dạng tĩnh.

## 12. Khả năng đọc và hiệu năng

Độ tương phản phải được đánh giá trên **màu sau khi hòa trộn**, ở cả vùng nền sáng và tối. Không thể kết luận chữ đạt độ tương phản chỉ bằng HEX của chữ và tint chưa compositing.

- Đặt đoạn văn trên vùng nền tương đối yên; tránh đường sáng chạy ngang chữ.
- Nếu cần thêm độ che, tăng tint cục bộ dưới vùng đọc trước khi làm đục toàn bộ popup.
- Focus dùng outline sắc, không chỉ một quầng sáng khó phân biệt.
- Trạng thái lỗi, thành công và lựa chọn có thêm chữ/icon/dấu hiệu hình học.
- Kiểm tra tiếng Việt nhiều dấu, nhãn dài, zoom và màn hình nhỏ.
- Hạn chế số bề mặt blur lớn, đặc biệt khi cuộn; giảm blur/chi tiết nền nếu thiết bị render chậm.
- Không giả định CSS mẫu đã được kiểm chứng về contrast, khả năng truy cập hoặc hiệu năng trên mọi thiết bị.

## 13. Các lỗi khiến style bị lệch

| Lỗi | Kết quả | Cách chỉnh |
| --- | --- | --- |
| Background component opaque | Card đặc, không nhìn xuyên được | Dùng màu có alpha |
| Giảm opacity của cả component | Chữ và icon nhạt theo | Chỉ giảm alpha của nền |
| Blur quá lớn | Kính sữa, mất chi tiết background | Giảm blur, kiểm tra bằng nét chạy sau kính |
| Nền hoàn toàn phẳng | Không nhận ra tính trong suốt | Thêm biến thiên sáng tối nhẹ |
| Scrim quá tối | Popup có alpha nhưng trông vẫn đục | Giảm độ che của scrim |
| Glow trắng/cyan quá mạnh | Cháy sáng, mất đường viền | Giảm alpha quầng, giữ lõi sắc |
| Mọi component đều glow mạnh | Không có phân cấp | Dành mức sáng cao cho điểm trọng tâm |
| Lồng nhiều card kính | Tích lũy tint, nặng và đục | Dùng một surface chung và divider |
| Mọi góc đều bo rất tròn | Mất cấu trúc hình học công nghệ | Dùng radius vừa phải |
| Họa tiết sci-fi quá dày | Nhiễu và khó đọc | Giữ khoảng tối và giảm chi tiết |
| Sao chép độ nhòe của JPG | UI thiếu sắc nét | Tách glow khỏi lõi chữ và nét |
| Panel nổi thiếu `position: fixed` | Panel teleport ra `body` nhưng render ở flow thường cuối trang, ngoài màn hình, không thao tác được | Class panel phải có `position: fixed` + `top`/`left` inline tính từ trigger (mục 7.3) |
| Panel nổi đặt trong card kính, chỉ tăng z-index | Bị che bởi card sibling vì `backdrop-filter` tạo stacking context | Teleport ra `body` + `position: fixed` (mục 7.3) |

## 14. Checklist đối chiếu khi thiết kế

- [ ] Nền có chiều sâu navy/indigo và vùng sáng cyan/violet cục bộ.
- [ ] Nhìn thấy sự liên tục của một chi tiết nền trước và sau mặt kính.
- [ ] Popup vẫn cho cảm giác xuyên thấu sau khi bật scrim.
- [ ] Alpha áp dụng lên bề mặt, không làm nhạt toàn bộ nội dung.
- [ ] Viền mảnh, lõi rõ; quầng sáng không nuốt mất đường nét.
- [ ] Phản xạ chỉ chiếm một vùng và đủ nhẹ.
- [ ] Cyan chiếm vai trò chính, violet/mint là accent có chủ đích.
- [ ] Không có nhiều tầng kính làm mặt trong cùng trở nên đặc.
- [ ] Body text sắc, không glow, đọc được trên các nền dự kiến.
- [ ] Focus, hover, selected và disabled phân biệt được.
- [ ] Trên mobile, chi tiết nền và blur vẫn phục vụ nội dung.
- [ ] Panel nổi (popover/menu/combobox) teleport ra `body` + `position: fixed`, mở đúng vị trí trigger và thao tác được.
- [ ] So sánh riêng với ảnh 1 về độ mềm và ảnh 2 về nét hologram.

## 15. Thực thi: Prism · Neon Design System

Phong cách này đã được triển khai thành bộ thư viện **Prism** trong `neon-design-system/` (Vue 3 + Vite, `@prism/neon-design-system` v0.8.0, `private: true`). Đây là nguồn tham chiếu chính khi áp dụng style vào sản phẩm.

### 15.1. Kiến trúc token (3 lớp)

Trong `src/tokens.css`, token chia 3 lớp theo thứ tự ưu tiên (lớp sau override lớp trước):

1. **Global** — palette gốc + scale kỹ thuật: màu `--ds-color-*`, spacing `--ds-space-1…16`, radius `--ds-radius-sm/md/lg`, font `--ds-font-sans/mono`, chuyển động `--ds-duration-fast/normal` + `--ds-ease`.
2. **Semantic** — ý nghĩa: `--ds-color-canvas`, `--ds-color-text-*`, `--ds-color-accent/success/danger/warning`, `--ds-color-border(-strong)`, vật liệu kính `--ds-surface-alpha/blur/tint/reflection`, `--ds-shadow-glass/glow`, `--ds-focus-ring`, `--ds-control-height`.
3. **Component** — theo component: `--ds-button-*-background`, `--ds-input-*-background`, `--ds-dialog-*`, `--ds-toast-background`, `--ds-list-item-*`.

Mọi bề mặt kính dùng **một class duy nhất `.p-glass`** (mục 10) đọc từ token semantic — nên đổi vật liệu toàn hệ chỉ bằng cách override token, không sửa từng component.

### 15.2. Danh mục component (56)

Xuất qua `src/components/index.js` (barrel). Nhóm theo `src/catalog.js`:

| Nhóm | Component |
| --- | --- |
| **Actions (1)** | `PrismButton` (primary/secondary/ghost/danger/loading/disabled) |
| **Forms (13)** | `PrismInput`, `PrismNumberInput`, `PrismPasswordInput`, `PrismTextarea`, `PrismSelect`, `PrismCombobox`, `PrismFileUpload`, `PrismCheckbox`, `PrismRadio` + `PrismRadioGroup`, `PrismSwitch`, `PrismSlider`, `PrismFormField` |
| **Date & time (3)** | `PrismDatePicker`, `PrismDateRangePicker`, `PrismDatetimePicker` (calendar nội bộ `PrismCalendar`) |
| **Feedback (7)** | `PrismBadge`, `PrismAlert`, `PrismToast`, `PrismProgress`, `PrismSpinner`, `PrismSkeleton`, `PrismEmptyState` |
| **Overlays (5)** | `PrismDialog`, `PrismDrawer`, `PrismTooltip`, `PrismPopover`, `PrismDropdownMenu` |
| **Data & navigation (13)** | `PrismTable`, `PrismPagination`, `PrismCard`, `PrismTabs`, `PrismAccordion`, `PrismAvatar` + `PrismAvatarGroup`, `PrismTimeline`, `PrismStat`, `PrismTree`, `PrismList`, `PrismBreadcrumbs`, `PrismGrid` |
| **Charts (9)** — SVG thuần, `role="img"` | `PrismBarChart`, `PrismLineChart`, `PrismDonutChart`, `PrismSparkline`, `PrismAreaChart`, `PrismRadarChart`, `PrismGauge`, `PrismHeatmap`, `PrismStackedBarChart` |
| **Content (5)** | `PrismAmbientBackground`, `PrismCodeBlock`, `PrismDivider`, `PrismKbd`, `PrismColorPicker` |

Ghi chú: `PrismCalendar` là component nội bộ (dùng chung cho 3 date picker), không tính vào nhóm public; tổng file SFC là 57.

### 15.3. Vật liệu & hành vi đã triển khai

- **Kính xuyên thấu:** surface alpha 10% + blur 4px (mặc định v0.8.0), dialog 40% + blur 7px, scrim navy 30%. Không giảm `opacity` của cả component — chỉ alpha lớp tint.
- **Nền không gian:** `PrismAmbientBackground` (5 radial glow + orb + plexus + bokeh + dot grid + noise + vignette), `aria-hidden`, `pointer-events: none`, z-index 0.
- **Panel nổi:** teleport ra `body` + `position: fixed` (z-index 85 panel / 90 date popup, tooltip), tính vị trí từ `getBoundingClientRect()`, tự lật/kẹp viewport, recalibrate khi resize/scroll — do `backdrop-filter` tạo stacking context (mục 7.3). Logic dùng chung `useFloatingPanel` / `useDatePopup`.
- **Chuyển động:** `--ds-duration-fast` 160ms, `--ds-ease` `cubic-bezier(.33,1,.68,1)`; dialog/drawer có animation mở; reduced motion tắt animation.
- **Fallback:** không hỗ trợ `backdrop-filter` → tăng tint; reduced motion → tắt animation/transition.
- **Theme:** demo có 2 theme (`prism` 10%/4px, `aipro` 7%/8px) + Theme Lab chỉnh realtime (mục 5.2).

### 15.4. Cấu trúc thư mục & chạy

```text
neon-design-system/
  src/
    tokens.css             Global → semantic → component tokens
    components.css         Chất liệu + state của public components
    components/            57 Vue SFC + barrel (index.js) + unit tests
    index.js               Public library entry (kèm stylesheet)
    App.vue                Catalog, search, filters, patterns, Glass Lab
    Hologram.vue           Minh họa giao diện bằng SVG/CSS
    ShowcasePanel.vue      Preview/code cho từng nhóm
    showcase.css           Layout riêng của ứng dụng tài liệu
    useFloatingPanel.js    Composable panel nổi dùng chung
  docs/
    COMPONENTS.md          Hợp đồng API và ví dụ
    ROADMAP.md             Phạm vi, ưu tiên, governance
  tests/
    showcase.spec.js       Playwright + axe + screenshot desktop/mobile
```

```sh
cd neon-design-system
npm ci
npm run dev        # showcase http://127.0.0.1:5173
npm run build      # dist/ (showcase) + dist-library/ (thư viện)
npm test           # vitest (hợp đồng component)
npm run test:e2e   # Playwright + axe + responsive + screenshot
```

Import trong dự án Vue:

```vue
<script setup>
import { PrismButton, PrismInput, PrismDialog } from './components'
import './tokens.css'
import './components.css'
</script>
```

Sau build, import từ `dist-library/prism.js` + `dist-library/prism.css`. Vue và `lucide-vue-next` là dependency external.

Chi tiết hợp đồng API từng component: `neon-design-system/docs/COMPONENTS.md`. Lịch sử thay đổi: `neon-design-system/CHANGELOG.md`.

## 16. Brief ngắn để tái sử dụng

> Thiết kế giao diện Futuristic Neon Glass trên nền navy–indigo sâu, có ánh sáng cyan và violet cục bộ. Component là các tấm kính nhuộm xanh tím alpha thấp, nhìn xuyên thấy mảng màu và đường nét background. Dùng blur nhẹ, viền cyan mảnh với lõi sắc, quầng sáng mềm và phản xạ chéo rất nhẹ. Tạo chiều sâu bằng chồng lớp, mép kính và ánh sáng định hướng. Popup dùng scrim navy nhẹ để giữ cảm giác xuyên thấu; phần chữ luôn sắc nét, đủ nổi trên nền. Icon dạng outline/wireframe, hình học bo nhẹ, accent tím có tiết chế. Giữ nhiều khoảng tối, tránh nền card đặc, kính trắng đục và neon tràn lan.
