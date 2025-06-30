-- 定义变量
-- \set URL '/education/classes/export'
-- \set SERVER_NAME 'service-education'
--
-- -- 查询权限信息
-- WITH target_permission AS (
--     SELECT id, name, url, server_name
--     FROM service_user.permission
--     WHERE url = :'URL' AND server_name = :'SERVER_NAME'
-- )
-- -- 查询包含该权限的所有权限组
-- SELECT
--     pg.name AS group_name,
--     p.id AS permission_id,
--     p.name AS permission_name,
--     p.url,
--     p.server_name
-- FROM service_user.permission_group pg
--          JOIN unnest(pg.permission_id) AS perm_id(id)
-- ON perm_id.id = (SELECT id FROM target_permission)
--     JOIN service_user.permission p
--     ON perm_id.id = p.id;

-- 纯sql
-- 创建临时表存储查询参数

CREATE TEMP TABLE temp_params (
                                  param_name TEXT PRIMARY KEY,
                                  param_value TEXT
);

-- 插入参数值
INSERT INTO temp_params (param_name, param_value) VALUES
                                                      ('URL', '/education/classes/export'),
                                                      ('SERVER_NAME', 'service-education');

-- 查询权限信息并关联权限组
WITH
    params AS (SELECT * FROM temp_params),
    target_permission AS (
        SELECT id, name, url, server_name
        FROM service_user.permission
        WHERE
            url = (SELECT param_value FROM params WHERE param_name = 'URL')
          AND server_name = (SELECT param_value FROM params WHERE param_name = 'SERVER_NAME')
    )
SELECT
    pg.name AS group_name,
    p.id AS permission_id,
    p.name AS permission_name,
    p.url,
    p.server_name
FROM service_user.permission_group pg
         JOIN unnest(pg.permission_id) AS perm_id(id)
              ON perm_id.id = (SELECT id FROM target_permission)
         JOIN service_user.permission p
              ON perm_id.id = p.id;

-- 清理临时表
DROP TABLE temp_params;
