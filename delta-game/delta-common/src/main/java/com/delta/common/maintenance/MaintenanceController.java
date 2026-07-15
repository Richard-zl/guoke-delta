package com.delta.common.maintenance;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 简单的纯 HTML 维护开关页面：
 * 访问 /maintenance 显示当前状态，并可以切换开启/关闭所有接口。
 */
@RestController
@RequiredArgsConstructor
public class MaintenanceController {

    private final MaintenanceState maintenanceState;

    /**
     * 提供一个简单的 JSON 状态接口，便于调试：
     * GET /maintenance/state -> {"enabled":true/false}
     */
    @GetMapping("/maintenance/state")
    public java.util.Map<String, Object> state() {
      return java.util.Collections.singletonMap("enabled", maintenanceState.isEnabled());
    }

    @GetMapping(value = "/maintenance", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public String maintenancePage() {
        boolean enabled = maintenanceState.isEnabled();
        String statusText = enabled ? "当前状态：<span style='color:#07c160'>对外开放</span>"
                                    : "当前状态：<span style='color:#ee0a24'>维护中（接口已关闭）</span>";
        String onChecked = enabled ? "checked" : "";
        String offChecked = !enabled ? "checked" : "";

        return """
<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <title>系统维护开关</title>
  <style>
    body { font-family: -apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif;
           background:#f5f7fa; padding:40px; }
    .card { max-width:520px; margin:0 auto; background:#fff; border-radius:8px;
            box-shadow:0 2px 8px rgba(0,0,0,0.06); padding:24px 28px; }
    h1 { font-size:22px; margin:0 0 12px; }
    .status { margin:8px 0 20px; font-size:14px; color:#606266; }
    .radio-group { margin-bottom:20px; }
    label { display:block; margin:6px 0; font-size:14px; color:#303133; }
    button { padding:8px 22px; border-radius:4px; border:none; background:#409EFF;
             color:#fff; font-size:14px; cursor:pointer; }
    button:hover { background:#66b1ff; }
    .tip { margin-top:18px; font-size:12px; color:#909399; line-height:1.6; }
  </style>
</head>
<body>
  <div class="card">
    <h1>系统维护开关</h1>
    <div class="status">%s</div>
    <form method="post" action="/maintenance/set">
      <div class="radio-group">
        <label><input type="radio" name="enabled" value="true" %s> 对外开放（所有接口可访问）</label>
        <label><input type="radio" name="enabled" value="false" %s> 维护模式（除本页面外全部接口关闭）</label>
      </div>
      <button type="submit">保存设置</button>
    </form>
    <div class="tip">
      提示：切换为“维护模式”后，除了本页面 <code>/maintenance</code> 以外，所有后端接口都会返回 503，
      前端会视为系统维护中不可用。刷新本页面即可查看最新状态。
    </div>
  </div>
</body>
</html>
""".formatted(statusText, onChecked, offChecked);
    }

    @PostMapping("/maintenance/set")
    public void setMaintenance(@RequestParam("enabled") String enabled,
                               HttpServletResponse response) throws IOException {
        boolean flag = "true".equalsIgnoreCase(enabled);
        maintenanceState.setEnabled(flag);
        // 修改后重定向回页面，避免重复提交
        response.sendRedirect("/maintenance");
    }
}

