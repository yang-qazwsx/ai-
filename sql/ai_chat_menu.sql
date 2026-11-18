-- AI 智能客服 菜单（目录 + 页面），授权给 admin 角色
-- 路由由 sys_menu 动态生成：目录 component 为空时后端自动置为 Layout，
-- 页面 component = 'ai/aichat/index' 解析为 @/views/ai/aichat/index.vue
-- 幂等：已存在同名菜单则跳过（menu_id 为自增，重复执行不会建多份）

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_time, create_by, remark)
SELECT 'AI 智能客服', 0, 60, 'ai', NULL, '', NULL, 1, 0, 'M', '0', '0', '', 'message', NOW(), 'admin', 'AI 智能客服目录'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_name = 'AI 智能客服' AND parent_id = 0);

SET @ai_dir = (SELECT menu_id FROM sys_menu WHERE menu_name = 'AI 智能客服' AND parent_id = 0);

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_time, create_by, remark)
SELECT '智能客服', @ai_dir, 1, 'aichat', 'ai/aichat/index', '', NULL, 1, 0, 'C', '0', '0', 'system:ai:query', '#', NOW(), 'admin', 'AI 智能客服对话页面'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_name = '智能客服' AND parent_id = @ai_dir);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE menu_name IN ('AI 智能客服', '智能客服');
