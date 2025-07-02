package 校区相关信息;

import com.jiuaoedu.common.Result;
import com.jiuaoedu.contract.edu.education.api.IClassApi;
import com.jiuaoedu.contract.edu.education.pojo.ClassDTO;
import com.jiuaoedu.contract.edu.user.api.IUserServerApi;
import com.jiuaoedu.contract.edu.user.pojo.ThreeLevelSchoolAreaInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@FeignClient(value = "service-user")
@Component
interface IUserClientV2 extends IUserServerApi {
}

@Component
@FeignClient(value = "service-education")
interface IClassClient extends IClassApi {
}

/**
 * @author ZhangHaoRan or KinMan Zhang
 * @since 2025/7/2 09:48
 */
public class SchoolAreaFix {

    @Resource
    private IUserClientV2 userClientV2;

    @Resource
    private IClassClient classClient;
    public void fixSchoolAreaTemplate () {
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
                        } else if (result.getData() != null) {
                            oneList.addAll(result.getData());
                        }
                    }
                    return oneList;
                },
               CollectionProcessingUtils.arrayListSupplier()).apply(classIds);

        Map<Long, ClassDTO> classDTOMap = classDTOList.stream().collect(Collectors.toMap(ClassDTO::getClassId, v -> v));
    }
}
