package 导出.controller版;

import com.alibaba.fastjson.JSON;
import com.jiuaoedu.common.Page;
import com.jiuaoedu.contract.edu.servicepublic.pojo.exporttask.CreateTaskIn;
import com.jiuaoedu.servicepublic.export.service.ExportClient;
import com.jiuaoedu.threadpool.TaskPool;
import com.jiuaoedu.threadpool.TaskPoolFactory;
import com.jiuaoedu.web.util.RequestContextHolderUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import 导出.PageIn;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 通用导出控制器，提供泛型化的导出功能
 * @param <I> 查询条件输入类型，必须实现PageIn接口
 * @param <R> 查询结果类型
 * @param <E> 导出数据类型
 * @author ZhangHaoRan or KinMan Zhang
 * @since 2025/7/1 10:27
 */
public abstract class ExportController <I extends PageIn, R, E> {
    private final TaskPool es = TaskPoolFactory.getTaskPool(1, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(300), new ThreadPoolExecutor.AbortPolicy());
    private final Logger log = LoggerFactory.getLogger(getClass());
    @Resource
    private ExportClient exportClient;
    /**
     * 导出数据方法
     * @param in 查询条件
     * @return 导出任务ID
     */
    protected com.jiuaoedu.common.Result<Long> exportData(@RequestBody @Validated I in) {
        CreateTaskIn createTaskIn = new CreateTaskIn();
        createTaskIn.setOperatorId(RequestContextHolderUtils.getEmployeeId());
        createTaskIn.setFileNme(getExportFileName());
        in.setPageSize(Integer.MAX_VALUE);
        in.setPageNum(1);
        createTaskIn.setParams(JSON.toJSONString(in));
        Long taskId = exportClient.createExportTask(createTaskIn);

        es.submit(new ExportTask<>(
                taskId,
                () -> fetchAndConvertData(in),
                getExportClassType(),
                createTaskIn.getFileNme() + "_" + UUID.randomUUID(),
                exportClient,
                log
        ));

        return com.jiuaoedu.common.Result.success(taskId);
    }

    /**
     * 强制子类重写的导出接口（路径自定义）
     * @return 导出任务ID
     * @param in 查询条件
     * @see #exportData(I)
     * 使用方法：
     * * @Override
     * * @ApiOperation(value = "极运营/教务管理/班级/导出查询班级", response = ClassesQueryOut.class)
     * * @PostMapping(value = "/export", consumes = "application/json")
     * * public com.jiuaoedu.common.Result<Long> export(@RequestBody @Validated ClassesQueryIn classesQueryIn)
     * * {
     * *    return exportData(classesQueryIn);
     * * }
     */
    @PostMapping(consumes = "application/json")
    public abstract com.jiuaoedu.common.Result<Long> export(@RequestBody @Validated I in);

    /**
     * 获取导出文件的名称
     * @return 导出文件名称
     */
    protected abstract String getExportFileName();

    /**
     * 获取导出数据的Class类型
     * @return 导出数据的Class对象
     */
    protected abstract Class<E> getExportClassType();

    /**
     * 分页查询数据
     * @param in 查询条件
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 分页查询结果
     */
    protected abstract Page<R> queryData(I in, int pageNum, int pageSize);

    /**
     * 将查询结果转换为导出数据格式
     * @param result 查询结果对象
     * @return 导出数据对象
     */
    protected abstract E convertToExportFormat(R result);

    /**
     * 批量获取并转换数据
     * @param in 查询条件
     * @return 转换后的导出数据列表
     */
    private List<E> fetchAndConvertData(I in) {
        int pageSize = 20000;
        int pageNum = 1;
        boolean hasNextPage = true;
        List<E> exportData = new ArrayList<>();

        while (hasNextPage) {
            Page<R> pageResult = queryData(in, pageNum, pageSize);
            List<R> results = pageResult.getList();

            for (R result : results) {
                exportData.add(convertToExportFormat(result));
            }

            hasNextPage = pageResult.isHasNextPage();
            pageNum++;
        }

        return exportData;
    }
}
