# HOME — Index

> **File index tổng quát** của chức năng **Home** (theo Rule 3 — `tq.hop/RULE.md`).
> AI đọc file này TRƯỚC để định vị file liên quan khi phát triển / fix bug.
> Chi tiết nghiệp vụ / luồng / logic → xem [`home_sumary.md`](./home_sumary.md).

## Công dụng

Màn hình **Home** — landing screen mặc định sau Login, read-only dashboard: GNB (Header + Sidebar) dùng chung toàn app, 2 tab **Chart** (9 charts) / **Community** (greeting, KPI, community skill cards, New Request, AI News), banner admin.

## Trạng thái

| Mục | Giá trị |
|---|---|
| SRS version | 1.23 (Updated) |
| Scope chính | GNB + Home Screen (HOME-01..HOME-14) |
| Route | `/home` (sidebar item mặc định) |
| Trạng thái code | **Đã implement Phase 2** (frontend `HomeView.vue` 2 tabs + backend `HomeServiceImpl` community-skills/new-requests + GNB trong `AppShell.vue`) |

## Cấu trúc file (trong folder `home/`)

| File | Nội dung |
|---|---|
| `Home_Screen_SRS.md` | SRS đầy đủ: GNB (Header/Sidebar/user menu/notification bell), Home 2 tabs, KPI, community cards, New Request, AI News card, business rules, API, data dictionary, use cases, AC, checklist |
| `home_bug.md` | Bug log của chức năng Home (mỗi bug 1 mục, append, không xóa mục đã fix) |

## Mapping chi tiết

- Nghiệp vụ, luồng, logic chi tiết → [`home_sumary.md`](./home_sumary.md)
- Bug log → [`home_bug.md`](./home_bug.md)
- Tài liệu liên quan (ngoài folder — chỉ tham chiếu, KHÔNG sửa khi làm Home):
  - `../1. Skill Management module/Skill_Management_Screen_Spec.md` (đích điều hướng của KPI/banner)
  - `../2. User Management module/Login_User Register before login_SRS.md` §58 (Login → Home)
  - `../2. User Management module/Settings_SRS.md` SH-03-04 (date format)
  - `../ai-news/AI_News_Screen_SRS.md` (màn hình đích khi click title tin AI News)
  - `../../neon-design-system/` (AppShell, PrismGrid, AppLogo)

## TODO / mở

- Subscribed KPI card: display-only v1.0 — PO chưa xác nhận có thêm filter "Subscribed" ở Skill Management không.
- Notification Center đầy đủ (settings/archive/history) — SRS riêng.
