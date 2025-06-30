package 数组分片;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author ZhangHaoRan or KinMan Zhang
 * @since 2025/6/30 10:33
 */
public class 导出分页分片 {
    private final TaskPool es = TaskPoolFactory.getTaskPool(1, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(300), new ThreadPoolExecutor.AbortPolicy());
    @Resource
    private ExportClient exportClient;
    /**
     * @param classesQueryIn 查寻条件入参
     * @return 班级列表
     */
    @ApiOperation(value = "极运营/教务管理/班级/导出查询班级", response = ClassesQueryOut.class)
    @PostMapping(value = "/export", consumes = "application/json")
    public com.jiuaoedu.common.Result<Long> exportClasses(@RequestBody @Validated ClassesQueryIn classesQueryIn) {
        CreateTaskIn createTaskIn = new CreateTaskIn();
        createTaskIn.setOperatorId(RequestContextHolderUtils.getEmployeeId());
        createTaskIn.setFileNme("导出班级");
        classesQueryIn.setPageSize(Integer.MAX_VALUE);
        classesQueryIn.setPageNum(1);
        createTaskIn.setParams(JSON.toJSONString(classesQueryIn));
        Long taskId = exportClient.createExportTask(createTaskIn);
        es.submit(new ExecutorTask() {
            @Override
            public void execute() {
                try {
                    List<ClassesQueryExportVo> exportDto = getClassesQueryExportVo(classesQueryIn);
                    exportSingleSheet(taskId, exportDto, ClassesQueryExportVo.class, createTaskIn.getFileNme() + "_" + UUID.randomUUID());
                } catch (Exception e) {
                    log.error("班级导出失败", e);
                    exportClient.failedExportTask(taskId, e.getMessage());
                }
            }
        });
        return com.jiuaoedu.common.Result.success(taskId);
    }

    private List<ClassesQueryExportVo> getClassesQueryExportVo(ClassesQueryIn classesQueryIn) {
        if (StringUtils.isBlank(classesQueryIn.getClassType()) && CollectionUtils.isEmpty(classesQueryIn.getClassTypes())) {
            List<String> classTypes = systemDataService.queryAvaliableClassTypes(classesQueryIn.getCourseType());
            classesQueryIn.setClassTypes(classTypes);
        }
        classesQueryIn.setOrderField(CommonUtils.camelToUnderline(classesQueryIn.getOrderField()));
        int pageSize = 20000; // 每页大小
        int pageNum = 1; // 起始页码
        boolean hasNextPage = true; // 是否有下一页
        List<ClassesQueryOut> classesQueryOutList = new ArrayList<>();
        while (hasNextPage) {
            // 设置当前分页参数
            classesQueryIn.setPageSize(pageSize);
            classesQueryIn.setPageNum(pageNum);
            Page<ClassesQueryOut> classesQueryOutPage = eduClassesService.queryClasses(classesQueryIn);
            classesQueryOutList.addAll(classesQueryOutPage.getList());
            hasNextPage = classesQueryOutPage.isHasNextPage();
            pageNum++;
        }

        List<ClassesQueryExportVo> exportDtos = new ArrayList<>();
        for (ClassesQueryOut classes : classesQueryOutList) {
            ClassesQueryExportVo exportDto = new ClassesQueryExportVo();
            BeanUtils.copyProperties(classes, exportDto);
            exportDto.setTeachType(classes.getTeachType() == null ? "-" : classes.getTeachType().getDesc());
            exportDto.setTextbookName(classes.getTextbook() == null ? "-" : classes.getTextbook().getEdition() + classes.getTextbook().getName());
            exportDto.setOutlandClass(Boolean.TRUE.equals(classes.getOutlandClass()) ? "是" : "否");
            exportDto.setReportFormContinued(Boolean.TRUE.equals(classes.getReportFormContinued()) ? "是" : "否");
            exportDto.setPhase(classes.getPhase() == null ? "-" : classes.getPhase() + "期");
            if (classes.getIsNormal() == null)
                exportDto.setIsNormal("-");
            else exportDto.setIsNormal(classes.getIsNormal() == 1 ? "正价" : "低价");
            if (classes.getCourseNumber() == null) {
                exportDto.setProgress("-");
            } else
                exportDto.setProgress(classes.getHasClassTimes() == null ? "0" : classes.getHasClassTimes() + "/" + classes.getCourseNumber());
            // 处理教学形式
            if (StringUtils.isNotBlank(exportDto.getTeachForm())) {
                String newTeachForm = TEACH_FORM_MAPPING.get(exportDto.getTeachForm());
                if (newTeachForm != null) {
                    exportDto.setTeachForm(newTeachForm);
                }
            }
            // 处理课程类型
            String courseType = exportDto.getCourseType();
            if (courseType != null) {
                String newCourseType = COURSE_TYPE_MAPPING.get(courseType);
                if (newCourseType != null) {
                    exportDto.setCourseType(newCourseType);
                }
            }
            exportDtos.add(exportDto);
        }
        return exportDtos;
    }

    private void exportSingleSheet(Long taskId, List<?> dataList, Class<?> modelClass, String fileName) {
        Map<String, List<?>> datas = new HashMap<>(4);
        datas.put("sheet1", dataList);
        Map<String, Class<?>> models = new HashMap<>(4);
        models.put("sheet1", modelClass);
        exportClient.exportFileWithMultipleSheet(datas, models, fileName + UUID.randomUUID().toString().substring(0, 5) + ".xlsx", taskId);
    }
}
