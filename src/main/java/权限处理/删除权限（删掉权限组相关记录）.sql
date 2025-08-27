-- 步骤1：创建临时表存储需要处理的目标ID
CREATE TEMPORARY TABLE temp_target_ids (
                                           target_id int8  -- 与permission_id数组元素类型一致（int8）
);

-- 插入指定的ID列表
INSERT INTO temp_target_ids (target_id) SELECT
    id  -- 若需指定字段（如URL、接口名、所属服务等），可替换为具体字段，例：url, api_name, service_name
FROM
    permission  -- 【必填】替换为实际存储URL的表名（如interface_list、api_config等）
WHERE
    url IN (
        -- 第一组：diagnosis相关URL
            '/diagnosis/queryDiagnosticStudentsList',
            '/diagnosis/queryDiagnosticStudentsPageList',
            '/diagnosis/queryDiagnosticDetailList',
            '/diagnosis/exam/paper/diagnosis/report/detail',

        -- 第二组：carousel/cms相关URL
            '/carousel/cms/getBannerCmsList',
            '/carousel/cms/getBannerCms',
            '/carousel/cms/addBannerCms',
            '/carousel/cms/bannerCms',
            '/carousel/cms/bannerCms/applet',

        -- 第三组：package/course相关URL
            '/package/course/detail',

        -- 第四组：goods/course相关URL
            '/goods/course/saveGoods',
            '/goods/course/goodsList',
            '/goods/course/goodsDetail',
            '/goods/course/updateGoodsStatus',
            '/goods/course/deleteGoods',

        -- 第五组：package/course（重复分类，按原输入整理）相关URL
            '/package/course/savePackage',
            '/package/course/packageList',
            '/package/course/packageDetail',
            '/package/course/updatePackageStatus',
            '/package/course/deletePackage'
        );

-- 步骤2：更新permission_group表，从permission_id数组中移除临时表中的ID
-- 逻辑：保留数组中不在temp_target_ids的元素，重新构建数组
UPDATE service_user.permission_group pg
SET permission_id = (
    SELECT array_agg(perm_id)  -- 重新聚合剩余的ID
    FROM unnest(pg.permission_id) AS perm_id  -- 拆分原数组为单行记录
    WHERE perm_id NOT IN (SELECT target_id FROM temp_target_ids)  -- 排除目标ID
);

-- 步骤3：删除permission_id为空数组的记录
DELETE FROM service_user.permission_group
WHERE permission_id = '{}'::int8[];  -- 匹配空int8数组

-- 可选：查看处理结果（验证用）
SELECT id, name, permission_id
FROM service_user.permission_group
WHERE id IN (
    -- 筛选出有变化的记录（原数组包含目标ID）
    SELECT DISTINCT pg.id
    FROM service_user.permission_group pg,
         unnest(pg.permission_id) AS perm_id
    WHERE perm_id IN (SELECT target_id FROM temp_target_ids)
)
   OR permission_id = '{}'::int8[];  -- 查看已删除的记录（若步骤3未执行）
