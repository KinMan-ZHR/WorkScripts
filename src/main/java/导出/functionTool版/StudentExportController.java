package 导出.functionTool版;

import com.jiuaoedu.common.Result;
import com.jiuaoedu.contract.edu.education.api.IClassApi;
import com.jiuaoedu.contract.edu.education.pojo.ClassesQueryDTO;
import com.jiuaoedu.contract.edu.education.pojo.ClassesQueryVo;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import javax.annotation.Resource;
import java.util.List;

public class StudentExportController {
@Resource
private ExportTool exportTool;
@Resource
private IClassApi classApi;
public List<ClassesQueryDTO> queryStudents(ClassesQueryVo in) { return classApi.queryClasses(in).getData().getList(); }
 public  ClassesQueryDTO convertToExportVo(ClassesQueryDTO student) {
        return student;
}
// 3. 创建导出处理器
 private final ExportTool.ExportHandler<ClassesQueryVo> exportHandler = exportTool.createExportHandlerNoPageProcess( this::queryStudents,  this::convertToExportVo,  "学生数据导出");
// 4. 控制器接口
@PostMapping("/api/students/export")
public Result<Long> export(@RequestBody ClassesQueryVo in) { return exportHandler.handle(in); } }