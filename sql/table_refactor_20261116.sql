-- ============================================================
-- AI 智能客服 表结构整改 DDL（已执行，存档备查）
-- 执行时间：2026-11-16
-- 库：ry-cloud
--
-- 内容：
--   1. 删除 3 组冗余索引（左前缀重叠）
--   2. 统一 biz_book 排序规则为 utf8mb4_0900_ai_ci
--   3. 修正 biz_book_content.parse_status 注释（3=仅可预览，非"失败"）
-- ============================================================

-- 1. 删冗余索引
ALTER TABLE `ai_chat_record` DROP INDEX `idx_user_id`;   -- 被 idx_user_time(user_id, chat_time) 覆盖
ALTER TABLE `ai_user_memory` DROP INDEX `idx_user`;       -- 被 idx_user_type(user_id, memory_type) 覆盖
ALTER TABLE `biz_book_chunk` DROP INDEX `idx_book`;       -- 被 uk_book_chunk(book_id, chunk_no) 覆盖

-- 2. 统一排序规则
ALTER TABLE `biz_book` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 3. 修正 parse_status 注释
ALTER TABLE `biz_book_content`
  MODIFY COLUMN `parse_status` tinyint DEFAULT '0'
  COMMENT '0待解析 1解析中 2成功 3仅可预览(无可抽取文本，如扫描版PDF)';

-- 附：未删除的预留死字段（已用 @Deprecated 标注，避免误导）：
--   ai_user_profile.preferred_categories / preferred_books
--   ai_user_memory.confidence / source_record_id / last_hit_time
--   保留原因：删除列需同步改 Mapper/实体/DDL，且 preferred_* 是画像合理扩展点；
--   若确定不用，可后续再删列。
