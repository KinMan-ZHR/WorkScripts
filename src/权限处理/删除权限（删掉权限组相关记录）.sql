-- -- 定义变量
-- BEGIN transaction ;
-- \set URL '/education/classes/export'
-- \set SERVER_NAME 'service-education'
-- \set GROUP_NAME '极运营/教务管理/班级/查看班级'
--
-- -- 从权限组中移除该权限ID
-- UPDATE service_user.permission_group
-- SET permission_id = array_remove(permission_id,
--                                  (SELECT id FROM service_user.permission
--                                   WHERE url = :'URL' AND server_name = :'SERVER_NAME')
--                     )
-- WHERE name = :'GROUP_NAME';
--
-- -- 删除权限记录
-- DELETE FROM service_user.permission
-- WHERE url = :'URL' AND server_name = :'SERVER_NAME';
-- COMMIT ;

-- 纯sql
-- 定义变量
-- 创建临时表存储参数
BEGIN transaction ;
CREATE TEMP TABLE temp_params (
                                  param_name TEXT PRIMARY KEY,
                                  param_value TEXT
) ON COMMIT DROP;

-- 插入参数值
INSERT INTO temp_params (param_name, param_value) VALUES
                                                      ('URL', '/education/classes/export'),
                                                      ('SERVER_NAME', 'service-education'),
                                                      ('GROUP_NAME', '极运营/教务管理/班级/查看班级');

-- 从权限组中移除该权限ID
WITH params AS (SELECT * FROM temp_params)
UPDATE service_user.permission_group
SET permission_id = array_remove(
        permission_id,
        (SELECT id FROM service_user.permission
         WHERE url = (SELECT param_value FROM params WHERE param_name = 'URL')
           AND server_name = (SELECT param_value FROM params WHERE param_name = 'SERVER_NAME'))
                    )
WHERE name = (SELECT param_value FROM params WHERE param_name = 'GROUP_NAME');

-- 删除权限记录
WITH params AS (SELECT * FROM temp_params)
DELETE FROM service_user.permission
WHERE url = (SELECT param_value FROM params WHERE param_name = 'URL')
  AND server_name = (SELECT param_value FROM params WHERE param_name = 'SERVER_NAME');

COMMIT ;