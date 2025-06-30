-- 通解：
-- 定义变量
    \set PERMISSION_NAME '极运营/教务管理/班级/导出班级V2'
    \set SERVER_NAME 'service-education'
    \set URL '/education/classes/export'
    \set HTTP_METHOD 'POST'
    \set LEVEL 'loginAndAuth'
    \set PERMISSION_TYPE 'URL'
    \set CHECK_RECOMMIT 'f'
    \set GROUP_NAME '极运营/教务管理/班级/查看班级'

-- 插入新权限
INSERT INTO "service_user"."permission" (
    "id", "name", "server_name", "url", "http_method",
    "create_time", "level", "front", "type", "remark",
    "check_recommit", "parent_id", "tree_level"
)
VALUES (
           nextval('service_user.permission_id_seq'),
           :'PERMISSION_NAME',
           :'SERVER_NAME',
           :'URL',
           :'HTTP_METHOD',
           now(),
           :'LEVEL',
           NULL,
           :'PERMISSION_TYPE',
           NULL,
           :'CHECK_RECOMMIT',
           NULL,
           NULL
       );

-- 更新权限组
UPDATE service_user.permission_group
SET permission_id = array_cat(
        permission_id,
        ARRAY(SELECT id FROM service_user.permission
          WHERE url = :'URL'
          AND server_name = :'SERVER_NAME')
                    )
WHERE name = :'GROUP_NAME'
  AND NOT permission_id @> ARRAY(SELECT id FROM service_user.permission
                                WHERE url = :'URL'
                                AND server_name = :'SERVER_NAME');

-- 纯sql：
-- 定义变量
BEGIN TRANSACTION;

-- 创建临时表存储变量
CREATE TEMP TABLE sql_vars (
                               var_name text,
                               var_value text
) on commit drop;

-- 插入变量值
INSERT INTO sql_vars (var_name, var_value) VALUES
                                               ('PERMISSION_NAME', '极运营/教务管理/班级/导出班级V2'),
                                               ('SERVER_NAME', 'service-education'),
                                               ('URL', '/education/classes/export'),
                                               ('HTTP_METHOD', 'POST'),
                                               ('LEVEL', 'loginAndAuth'),
                                               ('PERMISSION_TYPE', 'URL'),
                                               ('CHECK_RECOMMIT', 'f'),
                                               ('GROUP_NAME', '极运营/教务管理/班级/查看班级');

-- 插入新权限
WITH vars AS (SELECT * FROM sql_vars)
INSERT INTO "service_user"."permission" (
    "id", "name", "server_name", "url", "http_method",
    "create_time", "level", "front", "type", "remark",
    "check_recommit", "parent_id", "tree_level"
)
VALUES (
           nextval('service_user.permission_id_seq'),
           (SELECT var_value FROM vars WHERE var_name = 'PERMISSION_NAME'),
           (SELECT var_value FROM vars WHERE var_name = 'SERVER_NAME'),
           (SELECT var_value FROM vars WHERE var_name = 'URL'),
           (SELECT var_value FROM vars WHERE var_name = 'HTTP_METHOD'),
           now(),
           (SELECT var_value FROM vars WHERE var_name = 'LEVEL'),
           NULL,
           (SELECT var_value FROM vars WHERE var_name = 'PERMISSION_TYPE'),
           NULL,
           (SELECT CASE WHEN var_value = 't' THEN true ELSE false END
            FROM vars WHERE var_name = 'CHECK_RECOMMIT'),
           NULL,
           NULL
       );

-- 更新权限组
WITH vars AS (SELECT * FROM sql_vars)
UPDATE service_user.permission_group
SET permission_id = array_cat(
        permission_id,
        ARRAY(SELECT id FROM service_user.permission
              WHERE url = (SELECT var_value FROM vars WHERE var_name = 'URL')
                AND server_name = (SELECT var_value FROM vars WHERE var_name = 'SERVER_NAME'))
                    )
WHERE name = (SELECT var_value FROM vars WHERE var_name = 'GROUP_NAME')
  AND NOT permission_id @> ARRAY(SELECT id FROM service_user.permission
                                 WHERE url = (SELECT var_value FROM vars WHERE var_name = 'URL')
                                   AND server_name = (SELECT var_value FROM vars WHERE var_name = 'SERVER_NAME'));

COMMIT;

-- 原始特例
-- 新增一条权限
INSERT INTO "service_user"."permission" ("id", "name", "server_name", "url", "http_method", "create_time",
                                         "level", "front", "type", "remark", "check_recommit", "parent_id",
                                         "tree_level")
VALUES (nextval( 'service_user.permission_id_seq' ), '极运营/教务管理/班级/导出班级V2',
        'service-education', '/education/classes/export', 'POST', now(), 'loginAndAuth', NULL, 'URL', NULL, 'f', NULL, NULL);


-- 将该权限添加到指定权限组
UPDATE service_user.permission_group
SET permission_id = array_cat(
        permission_id,
        ARRAY(SELECT id FROM service_user.permission
              WHERE url = '/education/classes/export'
                AND server_name = 'service-education')
                    )
WHERE name = '极运营/教务管理/班级/查看班级'
  AND NOT permission_id @> ARRAY(SELECT id FROM service_user.permission
                                 WHERE url = '/education/classes/export'
                                   AND server_name = 'service-education');

