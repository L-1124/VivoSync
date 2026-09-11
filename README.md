<div align="center">

# VivoSync

**将高校课表同步到 vivo / iQOO 的 OriginOS 原生日历**

</div>

VivoSync 是一款 Android 课表同步工具。它从学校教务系统或 ICS 文件导入课程，转换为符合 RFC 5545 的周期日程，并写入 vivo OriginOS 原生日历，让课程能够继续显示在系统日历与桌面原子组件中。

> [!IMPORTANT]
> VivoSync 面向 vivo / iQOO 的 OriginOS 日历协议开发。其他品牌设备即使能够安装，也不保证课表视图、桌面组件或同步流程正常工作。

## 功能特性

- **多来源导入**：支持 ICS 文件与教务系统网页导入，内置 200+ 学校及通用教务适配入口。
- **原生日历集成**：通过 Android `CalendarProvider` 写入系统课程表，无需另装桌面组件。

## 运行要求

| 项目   | 要求                                   |
|------|--------------------------------------|
| 设备   | 推荐 vivo / iQOO 手机                    |
| 系统   | Android 8.1（API 27）及以上，建议使用 OriginOS |
| 权限   | 日历读取、日历写入；在线导入需要网络访问                 |
