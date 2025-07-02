package 导出;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * @author ZhangHaoRan or KinMan Zhang
 * @since 2025/7/1 11:26
 */
@ApiModel("每页")
public class PageIn {
    @ApiModelProperty(value = "页号")
    private Integer pageNum = 1;

    @ApiModelProperty(value = "每页显示条数")
    private Integer pageSize = 10;

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Integer getPageNum() {
        return pageNum;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }
}
