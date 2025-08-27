INSERT INTO "service_user"."permission" ("id", "name", "server_name", "url", "http_method", "create_time", "level", "front", "type", "remark", "check_recommit", "parent_id", "tree_level") VALUES
( nextval( 'service_user.permission_id_seq' ),
 '极运营/营销/提现记录/导出',
 'api-operation-web',
 '/v1/withdraw/export',
 'POST', NOW(),
 'loginButNoAuth',
 NULL,
 'URL',
 NULL,
 'f',
 NULL,
 NULL);

INSERT INTO "service_user"."permission" ("id", "name", "server_name", "url", "http_method", "create_time", "level", "front", "type", "remark", "check_recommit", "parent_id", "tree_level") VALUES
( nextval( 'service_user.permission_id_seq' ),
 '极运营/营销/转介绍发放/学员发放明细/导出v2',
 'service-finance',
 '/finance/introduce/record/deal/receive/list/export',
 'POST', NOW(),
 'loginButNoAuth',
 NULL,
 'URL',
 NULL,
 'f',
 NULL,
 NULL);

INSERT INTO "service_user"."permission" ("id", "name", "server_name", "url", "http_method", "create_time", "level", "front", "type", "remark", "check_recommit", "parent_id", "tree_level") VALUES
( nextval( 'service_user.permission_id_seq' ),
 '极运营/营销/转介绍发放/学员发放汇总/导出v2',
 'service-finance',
 '/finance/introduce/record/deal/receive/list-collection/export',
 'POST', NOW(),
 'loginButNoAuth',
 NULL,
 'URL',
 NULL,
 'f',
 NULL,
 NULL);

BEGIN TRANSACTION;
WITH inserted_permission AS (
INSERT INTO "service_user"."permission" (
    "id", "name", "server_name", "url", "http_method",
    "create_time", "level", "front", "type", "remark",
    "check_recommit", "parent_id", "tree_level"
)
    VALUES (
               nextval('service_user.permission_id_seq'),
               '极运营/设置/冻结管理/导出v2',
               'api-operation-web',
               '/freezeManage/export',
               'POST',
               now(),
               'loginAndAuth',
               NULL,
               'URL',
               NULL,
               false,
               NULL,
               NULL
           )
        RETURNING id
)

-- 更新权限组
UPDATE service_user.permission_group
SET permission_id = array_cat(
        permission_id,
        ARRAY(SELECT id FROM inserted_permission)
                    )
WHERE name = '极运营/系统设置/风控设置/冻结管理/导出'
  AND NOT permission_id @> ARRAY(SELECT id FROM inserted_permission);

COMMIT;

BEGIN TRANSACTION;
WITH inserted_permission AS (
    INSERT INTO "service_user"."permission" (
                                             "id", "name", "server_name", "url", "http_method",
                                             "create_time", "level", "front", "type", "remark",
                                             "check_recommit", "parent_id", "tree_level"
        )
        VALUES (
                   nextval('service_user.permission_id_seq'),
                   '极运营/设置/风控日志/冻结日志/导出v2',
                   'api-operation-web',
                   '/freezeLog/export',
                   'POST',
                   now(),
                   'loginAndAuth',
                   NULL,
                   'URL',
                   NULL,
                   false,
                   NULL,
                   NULL
               )
        RETURNING id
)

-- 更新权限组
UPDATE service_user.permission_group
SET permission_id = array_cat(
        permission_id,
        ARRAY(SELECT id FROM inserted_permission)
                    )
WHERE name = '极运营/系统设置/风控设置/风控日志/冻结日志/导出'
  AND NOT permission_id @> ARRAY(SELECT id FROM inserted_permission);

COMMIT;

BEGIN TRANSACTION;
WITH inserted_permission AS (
    INSERT INTO "service_user"."permission" (
                                             "id", "name", "server_name", "url", "http_method",
                                             "create_time", "level", "front", "type", "remark",
                                             "check_recommit", "parent_id", "tree_level"
        )
        VALUES (
                   nextval('service_user.permission_id_seq'),
                   '极运营/设置/风控日志/预警日志/导出v2',
                   'api-operation-web',
                   '/freezeLog/export',
                   'POST',
                   now(),
                   'loginAndAuth',
                   NULL,
                   'URL',
                   NULL,
                   false,
                   NULL,
                   NULL
               )
        RETURNING id
)

-- 更新权限组
UPDATE service_user.permission_group
SET permission_id = array_cat(
        permission_id,
        ARRAY(SELECT id FROM inserted_permission)
                    )
WHERE name = '极运营/系统设置/风控设置/风控日志/预警日志/导出'
  AND NOT permission_id @> ARRAY(SELECT id FROM inserted_permission);

COMMIT;

BEGIN TRANSACTION;
WITH inserted_permission AS (
    INSERT INTO "service_user"."permission" (
                                             "id", "name", "server_name", "url", "http_method",
                                             "create_time", "level", "front", "type", "remark",
                                             "check_recommit", "parent_id", "tree_level"
        )
        VALUES (
                   nextval('service_user.permission_id_seq'),
                   '极运营/报表/奖励金报表/导出v2',
                   'api-operation-web',
                   '/marketingAccount/bonus/report/export',
                   'POST',
                   now(),
                   'loginAndAuth',
                   NULL,
                   'URL',
                   NULL,
                   false,
                   NULL,
                   NULL
               )
        RETURNING id
)

-- 更新权限组
UPDATE service_user.permission_group
SET permission_id = array_cat(
        permission_id,
        ARRAY(SELECT id FROM inserted_permission)
                    )
WHERE name = '极运营/营销中心/转介绍/奖励金账户'
  AND NOT permission_id @> ARRAY(SELECT id FROM inserted_permission);

COMMIT;

BEGIN TRANSACTION;
WITH inserted_permission AS (
    INSERT INTO "service_user"."permission" (
                                             "id", "name", "server_name", "url", "http_method",
                                             "create_time", "level", "front", "type", "remark",
                                             "check_recommit", "parent_id", "tree_level"
        )
        VALUES (
                   nextval('service_user.permission_id_seq'),
                   '极运营/财务/奖励金流水明细/导出v2',
                   'api-operation-web',
                   '/marketingAccount/bonus/flow/export',
                   'POST',
                   now(),
                   'loginAndAuth',
                   NULL,
                   'URL',
                   NULL,
                   false,
                   NULL,
                   NULL
               )
        RETURNING id
)

-- 更新权限组
UPDATE service_user.permission_group
SET permission_id = array_cat(
        permission_id,
        ARRAY(SELECT id FROM inserted_permission)
                    )
WHERE name = '极运营/财务管理/奖励金流水明细/导出'
  AND NOT permission_id @> ARRAY(SELECT id FROM inserted_permission);

COMMIT;

BEGIN TRANSACTION;
WITH inserted_permission AS (
    INSERT INTO "service_user"."permission" (
                                             "id", "name", "server_name", "url", "http_method",
                                             "create_time", "level", "front", "type", "remark",
                                             "check_recommit", "parent_id", "tree_level"
        )
        VALUES (
                   nextval('service_user.permission_id_seq'),
                   '极运营/财务/账户统计/公司账户统计/导出',
                   'service-finance',
                   '/finance/area/accounts/count/export',
                   'POST',
                   now(),
                   'loginAndAuth',
                   NULL,
                   'URL',
                   NULL,
                   false,
                   NULL,
                   NULL
               )
        RETURNING id
)

-- 更新权限组
UPDATE service_user.permission_group
SET permission_id = array_cat(
        permission_id,
        ARRAY(SELECT id FROM inserted_permission)
                    )
WHERE name = '极运营/财务管理/异常对账单'
  AND NOT permission_id @> ARRAY(SELECT id FROM inserted_permission);

COMMIT;

BEGIN TRANSACTION;
WITH inserted_permission AS (
    INSERT INTO "service_user"."permission" (
                                             "id", "name", "server_name", "url", "http_method",
                                             "create_time", "level", "front", "type", "remark",
                                             "check_recommit", "parent_id", "tree_level"
        )
        VALUES (
                   nextval('service_user.permission_id_seq'),
                   '极运营/财务/账户统计/校区账户统计/导出',
                   'service-finance',
                   '/finance/school/accounts/count/export',
                   'POST',
                   now(),
                   'loginAndAuth',
                   NULL,
                   'URL',
                   NULL,
                   false,
                   NULL,
                   NULL
               )
        RETURNING id
)

-- 更新权限组
UPDATE service_user.permission_group
SET permission_id = array_cat(
        permission_id,
        ARRAY(SELECT id FROM inserted_permission)
                    )
WHERE name = '极运营/财务管理/异常对账单'
  AND NOT permission_id @> ARRAY(SELECT id FROM inserted_permission);

COMMIT;

BEGIN TRANSACTION;
INSERT INTO "service_user"."permission" ("id", "name", "server_name", "url", "http_method", "create_time",
                                         "level", "front", "type", "remark", "check_recommit", "parent_id",
                                         "tree_level")
VALUES (nextval( 'service_user.permission_id_seq' ),
        '极运营/导出任务/查询导出地址-v2',
        'service-public',
        '/exportTask/download-v2',
        'GET',
        NOW(),
        'loginButNoAuth',
        NULL,
        'URL',
        NULL,
        'f',
        NULL,
        NULL);
COMMIT;
