
package com.pricemanagement.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

@Data
@ColumnWidth(20)
public class UserExcelData {

    @ExcelProperty("用户名")
    private String username;

    @ExcelProperty("工号")
    private String employeeId;

    @ExcelProperty("昵称")
    private String nickname;

    @ExcelProperty("邮箱")
    private String email;

    @ExcelProperty("手机号")
    private String phone;

    @ExcelProperty("部门")
    private String department;

    @ExcelProperty("角色(ADMIN/EDITOR/VIEWER)")
    private String role;

    @ExcelProperty("状态(ACTIVE/INACTIVE)")
    private String status;

    @ExcelProperty("初始密码")
    private String password;
}
