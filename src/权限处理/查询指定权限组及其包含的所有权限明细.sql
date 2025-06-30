-- 定义变量
/*
\set GROUP_NAME '极运营/教务管理/班级/查看班级'

-- 查询权限组信息及包含的所有权限
SELECT
    pg.name AS group_name,
    p.id AS permission_id,
    p.name AS permission_name,
    p.url,
    p.http_method,
    p.server_name
FROM service_user.permission_group pg
         JOIN unnest(pg.permission_id) AS perm_id(id)
ON true
    JOIN service_user.permission p
    ON perm_id.id = p.id
WHERE pg.name = :'GROUP_NAME'
ORDER BY p.id;
*/
-- 纯sql
-- 创建临时表存储查询参数
CREATE TEMP TABLE temp_params (
                                  param_name TEXT PRIMARY KEY,
                                  param_value TEXT
);

-- 插入参数值
INSERT INTO temp_params (param_name, param_value) VALUES
    ('GROUP_NAME', '极运营/教务管理/班级/查看班级');

-- 查询权限组信息及包含的所有权限
WITH params AS (SELECT * FROM temp_params)
SELECT
    pg.name AS group_name,
    p.id AS permission_id,
    p.name AS permission_name,
    p.url,
    p.http_method,
    p.server_name
FROM service_user.permission_group pg
         JOIN unnest(pg.permission_id) AS perm_id(id) ON true
         JOIN service_user.permission p ON perm_id.id = p.id
WHERE pg.name = (SELECT param_value FROM params WHERE param_name = 'GROUP_NAME')
ORDER BY p.id;

-- 清理临时表
DROP TABLE temp_params;