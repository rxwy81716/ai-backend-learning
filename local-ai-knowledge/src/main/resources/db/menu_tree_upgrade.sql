-- ====================================================
-- 菜单树形结构升级 + 热榜菜单初始化
--
-- 执行前提：sys_menu 表已存在
-- 执行方式：手动在 PG 中执行一次即可
-- ====================================================

-- 1. 清空现有菜单数据（重新构建树形结构）
TRUNCATE sys_role_menu;
DELETE FROM sys_menu;

-- 重置自增序列
ALTER SEQUENCE sys_menu_id_seq RESTART WITH 1;

-- ====================================================
-- 2. 插入树形菜单（parent_id=0 为顶级）
-- ====================================================

-- === 顶级菜单 ===
INSERT INTO sys_menu (id, parent_id, name, path, component, icon, sort_order) VALUES
  (1,  0, '智能问答',   '/rag',       'rag/RagChat',              'ChatDotRound', 1),
  (2,  0, '文档管理',   '/documents', 'document/DocumentManage',  'Document',     2),
  (3,  0, '热榜中心',   '/hot/dashboard', NULL,                    'TrendCharts',  3),   -- 父菜单，redirect到第一个子菜单
  (4,  0, '个人中心',   '/profile',   'profile/UserProfile',      'User',         8),
  (5,  0, '系统管理',   '/admin',      NULL,                       'Setting',      10);  -- 父菜单，无组件

-- === 热榜中心 子菜单（parent_id=3） ===
INSERT INTO sys_menu (id, parent_id, name, path, component, icon, sort_order) VALUES
  (31, 3, '每日热榜',   '/hot/dashboard', 'hot/HotDashboard',    'Sunrise',  1),
  (32, 3, '历史热榜',   '/hot/history',   'hot/HotHistory',      'Clock',    2);

-- === 系统管理 子菜单（parent_id=5） ===
INSERT INTO sys_menu (id, parent_id, name, path, component, icon, sort_order) VALUES
  (51, 5, '用户管理',   '/admin/users',   'admin/UserManage',    'User',       1),
  (52, 5, '角色管理',   '/admin/roles',   'admin/RoleManage',    'UserFilled', 2),
  (53, 5, '菜单管理',   '/admin/menus',   'admin/MenuManage',    'Menu',       3),
  (54, 5, '智能体管理', '/admin/agents',  'admin/AgentManage',   'Cpu',        4),
  (55, 5, '爬虫管理',   '/admin/crawler', 'admin/CrawlerManage', 'Monitor',    5);

-- 更新自增序列到安全值
SELECT setval('sys_menu_id_seq', (SELECT MAX(id) FROM sys_menu));

-- ====================================================
-- 3. 角色菜单权限分配
-- ====================================================

-- 管理员（ROLE_ADMIN）：所有菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r, sys_menu m
WHERE r.code = 'ROLE_ADMIN'
ON CONFLICT (role_id, menu_id) DO NOTHING;

-- 普通用户（ROLE_USER）：智能问答 + 文档管理 + 热榜中心（含子菜单） + 个人中心
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r, sys_menu m
WHERE r.code = 'ROLE_USER'
  AND m.id IN (1, 2, 3, 31, 32, 4)
ON CONFLICT (role_id, menu_id) DO NOTHING;

-- ====================================================
-- 验证：查看树形结构
-- ====================================================
-- SELECT m.id, m.parent_id, m.name, m.path, m.icon, m.sort_order
-- FROM sys_menu m
-- ORDER BY m.parent_id, m.sort_order;
