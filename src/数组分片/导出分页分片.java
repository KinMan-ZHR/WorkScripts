package 数组分片;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author ZhangHaoRan or KinMan Zhang
 * @since 2025/6/30 10:33
 */
public class 导出分页分片 {
    Result<List<ThreeLevelSchoolAreaInfo>> schoolAreaResult = userClientV2.queryAllThreeLevelSchoolAreaInfo();
    List<ThreeLevelSchoolAreaInfo> schoolAreaInfos = new ArrayList<>();
        if (schoolAreaResult.isSuccess()) {
        schoolAreaInfos = schoolAreaResult.getData();
    }
    Map<Long, ThreeLevelSchoolAreaInfo> schoolAreaInfoMap = schoolAreaInfos.stream().collect(Collectors.toMap(ThreeLevelSchoolAreaInfo::getSchoolAreaId, v -> v));
    List<Long> classIds = objectPageInfo.getList().stream().map(ReturnQueryOut::getClassId).distinct().collect(Collectors.toList());
    List<ClassDTO> classDTOList = CollectionProcessingUtils.enhanceProcessor(classClient::batchQueryClasses, 20000, results -> {
                List<ClassDTO> oneList = new ArrayList<>();
                for (Result<List<ClassDTO>> result : results) {
                    if (result == null || !result.isSuccess()) {
                        throw com.jiuaoedu.common.BusinessException.of("查询班级对应校区信息失败！");
                    } else if ( result.getData() != null) {
                        oneList.addAll(result.getData());
                    }
                }
                return oneList;
            },
            CollectionProcessingUtils.arrayListSupplier()).apply(classIds);
}
