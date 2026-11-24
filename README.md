# 半小时记录 (TimeLog)

每半小时（整点和半点）弹出通知提醒记录当前在做什么。

- 通知上可直接点常用标签，或点“其他…”输入文字，无需打开 app
- 点通知进入 app，可选标签 + 备注；点列表中任意时段可补记/修改
- 免打扰时段（默认 23:00–07:00），设置里可改
- 统计：今天 48 格时间轴 + 今天/近7天/近30天各标签时长
- 导出 CSV 到“下载”文件夹
- 数据只保存在本机

## 构建
推送到 GitHub 的 main 分支后，Actions 会自动构建，
在 Actions 运行记录的 Artifacts 里下载 `timelog-apk`（app-debug.apk）安装。
本地构建：`gradle assembleDebug`（需 JDK 17 + Android SDK）。
