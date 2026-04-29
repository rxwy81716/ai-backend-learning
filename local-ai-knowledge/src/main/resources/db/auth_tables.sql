-- ============================================================
-- 用户认证 & 角色权限 建表脚本
-- 数据库：PostgreSQL
-- ============================================================

-- 1. 用户表
CREATE TABLE IF NOT EXISTS sys_user
(
    id         BIGSERIAL PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL UNIQUE,
    password   VARCHAR(200) NOT NULL, -- BCrypt 加密
    nickname   VARCHAR(50),
    email      VARCHAR(100),
    phone      VARCHAR(20),
    avatar     VARCHAR(500),
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_user IS '系统用户表';
COMMENT ON COLUMN sys_user.username IS '登录用户名（唯一）';
COMMENT ON COLUMN sys_user.password IS '密码（BCrypt）';
COMMENT ON COLUMN sys_user.nickname IS '昵称';
COMMENT ON COLUMN sys_user.enabled IS '是否启用';

-- 2. 角色表
CREATE TABLE IF NOT EXISTS sys_role
(
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(30) NOT NULL UNIQUE, -- ROLE_USER / ROLE_VIP / ROLE_ADMIN
    name        VARCHAR(50) NOT NULL,        -- 普通用户 / 会员 / 管理员
    description VARCHAR(200),
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_role IS '角色表';
COMMENT ON COLUMN sys_role.code IS '角色编码（Spring Security 使用）';
COMMENT ON COLUMN sys_role.name IS '角色名称（显示用）';

-- 3. 用户-角色关联表（多对多）
CREATE TABLE IF NOT EXISTS sys_user_role
(
    id      BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES sys_user (id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES sys_role (id) ON DELETE CASCADE,
    UNIQUE (user_id, role_id)
);

COMMENT ON TABLE sys_user_role IS '用户角色关联表';

CREATE INDEX IF NOT EXISTS idx_user_role_user ON sys_user_role (user_id);
CREATE INDEX IF NOT EXISTS idx_user_role_role ON sys_user_role (role_id);

-- 4. 初始化角色数据
INSERT INTO sys_role (code, name, description)
VALUES ('ROLE_USER', '普通用户', '默认注册角色，可上传文档和使用 RAG 问答'),
       ('ROLE_VIP', '会员', '高级用户，更高的调用配额和优先级'),
       ('ROLE_ADMIN', '管理员', '系统管理员，可管理用户、角色和系统配置')
ON CONFLICT (code) DO NOTHING;

-- 5. 初始化管理员账号（密码: admin123，BCrypt 加密）
-- 注意：生产环境请修改密码！
INSERT INTO sys_user (username, password, nickname)
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员')
ON CONFLICT (username) DO NOTHING;

-- 给 admin 分配管理员角色
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u,
     sys_role r
WHERE u.username = 'admin'
  AND r.code = 'ROLE_ADMIN'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- ============================================================
-- 菜单权限管理
-- ============================================================

-- 6. 菜单表
CREATE TABLE IF NOT EXISTS sys_menu
(
    id         BIGSERIAL PRIMARY KEY,
    parent_id  BIGINT       NOT NULL DEFAULT 0,    -- 父菜单ID，0为顶级
    name       VARCHAR(50)  NOT NULL,              -- 菜单名称
    path       VARCHAR(200) NOT NULL,              -- 路由路径
    component  VARCHAR(200),                       -- 组件路径
    icon       VARCHAR(50),                        -- 图标
    sort_order INT          NOT NULL DEFAULT 0,    -- 排序
    is_visible BOOLEAN      NOT NULL DEFAULT TRUE, -- 是否显示
    is_enabled BOOLEAN      NOT NULL DEFAULT TRUE, -- 是否启用
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_menu IS '系统菜单表';
COMMENT ON COLUMN sys_menu.parent_id IS '父菜单ID（0=顶级菜单）';
COMMENT ON COLUMN sys_menu.path IS '路由路径';
COMMENT ON COLUMN sys_menu.component IS '前端组件路径';
COMMENT ON COLUMN sys_menu.icon IS '菜单图标';
COMMENT ON COLUMN sys_menu.sort_order IS '排序号';
COMMENT ON COLUMN sys_menu.is_visible IS '是否在菜单栏显示';
COMMENT ON COLUMN sys_menu.is_enabled IS '是否启用（禁用后不可访问）';

-- 7. 角色-菜单关联表（多对多）
CREATE TABLE IF NOT EXISTS sys_role_menu
(
    id      BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL REFERENCES sys_role (id) ON DELETE CASCADE,
    menu_id BIGINT NOT NULL REFERENCES sys_menu (id) ON DELETE CASCADE,
    UNIQUE (role_id, menu_id)
);

COMMENT ON TABLE sys_role_menu IS '角色菜单关联表';

CREATE INDEX IF NOT EXISTS idx_role_menu_role ON sys_role_menu (role_id);
CREATE INDEX IF NOT EXISTS idx_role_menu_menu ON sys_role_menu (menu_id);

-- 8. 初始化菜单数据
INSERT INTO sys_menu (name, path, component, icon, sort_order)
VALUES ('智能问答', '/rag', '/views/rag/RagChat.vue', 'ChatDotRound', 1),
       ('用户管理', '/admin/users', '/views/admin/UserManage.vue', 'User', 10),
       ('角色管理', '/admin/roles', '/views/admin/RoleManage.vue', 'UserFilled', 11),
       ('菜单管理', '/admin/menus', '/views/admin/MenuManage.vue', 'Menu', 12),
       ('智能体管理', '/admin/agents', '/views/admin/AgentManage.vue', 'Robot', 13)
ON CONFLICT DO NOTHING;

-- 9. 给管理员分配所有菜单权限
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r,
     sys_menu m
WHERE r.code = 'ROLE_ADMIN'
ON CONFLICT (role_id, menu_id) DO NOTHING;

-- 给普通用户分配智能问答菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r,
     sys_menu m
WHERE r.code = 'ROLE_USER'
  AND m.path = '/rag'
ON CONFLICT (role_id, menu_id) DO NOTHING;
