-- Flyway migration V98: i18n keys for the interactive robot banner on the
-- Home page (public/home-banner/ loaded in an iframe behind the tab content).
-- The banner reads these via postMessage from HomeView (buildBannerI18n).

-- ── i18n keys (en/vi/ko) ───────────────────────────────────────────────────
INSERT INTO translation_keys (key, description) VALUES
    ('home.banner.caption', 'Banner slogan caption above the written slogan'),
    ('home.banner.board', 'Banner teacher-board headline'),
    ('home.banner.hint', 'Banner bottom hint line'),
    ('home.banner.aria', 'Aria label for the banner iframe'),
    ('home.banner.slogan.0', 'Banner written slogan 1'),
    ('home.banner.slogan.1', 'Banner written slogan 2'),
    ('home.banner.slogan.2', 'Banner written slogan 3'),
    ('home.banner.slogan.3', 'Banner written slogan 4'),
    ('home.banner.slogan.4', 'Banner written slogan 5'),
    ('home.banner.speech.wave', 'Robot speech: wave'),
    ('home.banner.speech.curious', 'Robot speech: curious'),
    ('home.banner.speech.turnLeft', 'Robot speech: turn left'),
    ('home.banner.speech.turnRight', 'Robot speech: turn right'),
    ('home.banner.speech.hop', 'Robot speech: hop'),
    ('home.banner.speech.peek', 'Robot speech: peek'),
    ('home.banner.speech.sway', 'Robot speech: sway'),
    ('home.banner.speech.dash', 'Robot speech: dash'),
    ('home.banner.speech.rest', 'Robot speech: rest'),
    ('home.banner.speech.love', 'Robot speech: love'),
    ('home.banner.speech.tease', 'Robot speech: tease'),
    ('home.banner.speech.inspect', 'Robot speech: inspect'),
    ('home.banner.speech.write', 'Robot speech: write slogan'),
    ('home.banner.speech.roam', 'Robot speech: roam'),
    ('home.banner.speech.surprised', 'Robot speech: surprised'),
    ('home.banner.speech.hello', 'Robot speech: hello'),
    ('home.banner.speech.dragged', 'Robot speech: dragged'),
    ('home.banner.speech.sleeping', 'Robot speech: sleeping'),
    ('home.banner.speech.awake', 'Robot speech: awake')
ON CONFLICT (key) DO NOTHING;

-- English
INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('home.banner.caption', 'AI · TECHNOLOGY · THE FUTURE'),
    ('home.banner.board', 'IDEAS FOR TOMORROW'),
    ('home.banner.hint', 'Move closer · Tap for hearts · Try dragging me!'),
    ('home.banner.aria', 'AI robot banner'),
    ('home.banner.slogan.0', 'AI opens doors. People lead the way.'),
    ('home.banner.slogan.1', 'Today''s technology. Tomorrow''s possibilities.'),
    ('home.banner.slogan.2', 'Create the future with artificial intelligence.'),
    ('home.banner.slogan.3', 'Turn bright ideas into real possibilities.'),
    ('home.banner.slogan.4', 'The future begins with curiosity.'),
    ('home.banner.speech.wave', 'Hello! Over here! ^^'),
    ('home.banner.speech.curious', 'Can I take a closer look? 👀'),
    ('home.banner.speech.turnLeft', 'A little turn to the left! ↶'),
    ('home.banner.speech.turnRight', 'Turning right! ↷'),
    ('home.banner.speech.hop', 'Let''s take a little flight! ✨'),
    ('home.banner.speech.peek', 'Peekaboo! Still there? ♡'),
    ('home.banner.speech.sway', 'La la la... what a lovely day! ♫'),
    ('home.banner.speech.dash', 'Whoosh! Try to catch me! ✨'),
    ('home.banner.speech.rest', 'Just watching the world... ☁'),
    ('home.banner.speech.love', 'Hehe, that tickles! ♡'),
    ('home.banner.speech.tease', 'Hey, don''t tease me! ><'),
    ('home.banner.speech.inspect', 'Let me find some ideas in these panels...'),
    ('home.banner.speech.write', 'I have an idea to share with you! ✎'),
    ('home.banner.speech.roam', 'A little flight, then I''ll be back!'),
    ('home.banner.speech.surprised', 'Whoa! You surprised me! 😳'),
    ('home.banner.speech.hello', 'Oh, hello there! ✨'),
    ('home.banner.speech.dragged', 'Wheee! Again, please! ✨'),
    ('home.banner.speech.sleeping', 'Recharging a little... zzz'),
    ('home.banner.speech.awake', 'I''m awake! Let''s play some more ♡')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

-- Vietnamese
INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('home.banner.caption', 'AI · CÔNG NGHỆ · TƯƠNG LAI'),
    ('home.banner.board', 'NHỮNG Ý TƯỞNG CHO NGÀY MAI'),
    ('home.banner.hint', 'Lại gần hơn · Chạm để gửi yêu thương · Thử kéo tôi đi!'),
    ('home.banner.aria', 'Banner robot AI'),
    ('home.banner.slogan.0', 'AI mở ra cánh cửa. Con người dẫn lối.'),
    ('home.banner.slogan.1', 'Công nghệ của hôm nay. Khả năng của ngày mai.'),
    ('home.banner.slogan.2', 'Tạo nên tương lai cùng trí tuệ nhân tạo.'),
    ('home.banner.slogan.3', 'Biến ý tưởng sáng thành hiện thực khả thi.'),
    ('home.banner.slogan.4', 'Tương lai bắt đầu từ sự tò mò.'),
    ('home.banner.speech.wave', 'Xin chào! Ở đây nè! ^^'),
    ('home.banner.speech.curious', 'Cho tôi nhìn kỹ hơn được không? 👀'),
    ('home.banner.speech.turnLeft', 'Xoay sang trái một chút! ↶'),
    ('home.banner.speech.turnRight', 'Xoay sang phải nè! ↷'),
    ('home.banner.speech.hop', 'Bay một chút thôi! ✨'),
    ('home.banner.speech.peek', 'Cú lù! Vẫn còn đó chứ? ♡'),
    ('home.banner.speech.sway', 'La la la... ngày hôm nay thật đẹp! ♫'),
    ('home.banner.speech.dash', 'Vút! Bắt tôi xem sao! ✨'),
    ('home.banner.speech.rest', 'Đang ngắm nhìn thế giới thôi... ☁'),
    ('home.banner.speech.love', 'Hehe, ngứa lắm! ♡'),
    ('home.banner.speech.tease', 'Ê, đừng trêu tôi nha! ><'),
    ('home.banner.speech.inspect', 'Để tôi tìm ý tưởng trong những bảng này...'),
    ('home.banner.speech.write', 'Tôi có một ý tưởng muốn chia sẻ! ✎'),
    ('home.banner.speech.roam', 'Bay một vòng rồi quay lại ngay!'),
    ('home.banner.speech.surprised', 'Trời! Bạn làm tôi giật mình! 😳'),
    ('home.banner.speech.hello', 'Ồ, chào bạn! ✨'),
    ('home.banner.speech.dragged', 'Wheee! Kéo thêm lần nữa đi! ✨'),
    ('home.banner.speech.sleeping', 'Sạc pin một chút thôi... zzz'),
    ('home.banner.speech.awake', 'Mình tỉnh rồi! Chơi tiếp nào ♡')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

-- Korean
INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('home.banner.caption', 'AI · TECHNOLOGY · THE FUTURE'),
    ('home.banner.board', '내일의 아이디어'),
    ('home.banner.hint', '가까이 오세요 · 탭하면 하트가요 · 드래그해 보세요!'),
    ('home.banner.aria', 'AI 로봇 배너'),
    ('home.banner.slogan.0', 'AI는 문을 열고, 사람은 길을 이끕니다.'),
    ('home.banner.slogan.1', '오늘의 기술, 내일의 가능성.'),
    ('home.banner.slogan.2', '인공지능으로 미래를 만듭니다.'),
    ('home.banner.slogan.3', '밝은 아이디어를 현실의 가능성으로.'),
    ('home.banner.slogan.4', '미래는 호기심에서 시작됩니다.'),
    ('home.banner.speech.wave', '안녕하세요! 여기예요! ^^'),
    ('home.banner.speech.curious', '좀 더 가까이 볼 수 있을까요? 👀'),
    ('home.banner.speech.turnLeft', '왼쪽으로 살짝! ↶'),
    ('home.banner.speech.turnRight', '오른쪽으로 돌아요! ↷'),
    ('home.banner.speech.hop', '살짝 날아볼까요! ✨'),
    ('home.banner.speech.peek', '피카부! 아직 거기 있죠? ♡'),
    ('home.banner.speech.sway', '라라라... 오늘도 참 좋은 날! ♫'),
    ('home.banner.speech.dash', '휙! 잡아보세요! ✨'),
    ('home.banner.speech.rest', '세상 구경하고 있어요... ☁'),
    ('home.banner.speech.love', '히히, 간지러워요! ♡'),
    ('home.banner.speech.tease', '야, 장난치지 마세요! ><'),
    ('home.banner.speech.inspect', '이 패널들에서 아이디어를 찾아볼게요...'),
    ('home.banner.speech.write', '나눌 아이디어가 있어요! ✎'),
    ('home.banner.speech.roam', '한 바퀴 날다가 바로 올게요!'),
    ('home.banner.speech.surprised', '우와! 놀랐어요! 😳'),
    ('home.banner.speech.hello', '어, 안녕하세요! ✨'),
    ('home.banner.speech.dragged', '우와아! 한 번 더! ✨'),
    ('home.banner.speech.sleeping', '잠시 충전 중... zzz'),
    ('home.banner.speech.awake', '깨웠어요! 계속 놀까요 ♡')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;
