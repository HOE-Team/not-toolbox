package components

import main.kotlin.icons.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 统一的 Material Symbols 图标管理对象。
 *
 * 集中暴露 [main.kotlin.icons] 包中定义的所有 Material Symbols 图标，
 * 供各处代码通过 `MaterialSymbols.XXX` 引用，避免直接 import 具体图标文件。
 */
object MaterialSymbols {
    /** 概览/首页 */
    val Home: ImageVector = home

    /** 登录 */
    val Login: ImageVector = login

    /** 客房服务/进程 */
    val RoomService: ImageVector = room_service

    /** 电池满格 */
    val BatteryAndroidFrameFull: ImageVector = battery_android_frame_full

    /** 电池充电闪电 */
    val BatteryAndroidFrameBolt: ImageVector = battery_android_frame_bolt

    /** 电池容量问号 */
    val BatteryAndroidFrameQuestion: ImageVector = battery_android_frame_question

    /** 心电图心脏 */
    val EcgHeart: ImageVector = ecg_heart

    /** 桌面屏幕 */
    val DesktopWindows: ImageVector = desktop_windows

    /** 宽高比 */
    val AspectRatio: ImageVector = aspect_ratio

    /** 平移缩放 */
    val PanZoom: ImageVector = pan_zoom

    /** 蓝牙 */
    val Bluetooth: ImageVector = bluetooth

    /** 工具 */
    val Build: ImageVector = build

    /** 终端 */
    val Terminal: ImageVector = terminal

    /** 设置 */
    val Settings: ImageVector = settings

    /** 关于 */
    val Info: ImageVector = info

    /** 内存 */
    val Memory: ImageVector = memory

    /** 备用内存 */
    val MemoryAlt: ImageVector = memory_alt

    /** 存储/磁盘 */
    val Storage: ImageVector = storage

    /** 主板/GPU */
    val DeveloperBoard: ImageVector = developer_board

    /** 交换/上下 */
    val SwapVert: ImageVector = swap_vert

    /** 下载 */
    val Download: ImageVector = download

    /** 上传 */
    val Upload: ImageVector = upload

    /** 路由器/网络 */
    val Router: ImageVector = router

    /** 局域网 */
    val Lan: ImageVector = lan

    /** 警告 */
    val Warning: ImageVector = warning

    /** 描述/文档 */
    val Description: ImageVector = description

    /** 在新窗口打开 */
    val OpenInNew: ImageVector = open_in_new

    /** 全部清除 */
    val ClearAll: ImageVector = clear_all

    /** 停止 */
    val Stop: ImageVector = stop

    /** 深色模式 */
    val DarkMode: ImageVector = dark_mode

    /** 调色板 */
    val Palette: ImageVector = palette

    /** 关闭 */
    val Close: ImageVector = close

    /** 下载关闭（无法获取） */
    val FileDownloadOff: ImageVector = file_download_off

    /** 下载完成（安装成功） */
    val DownloadDone: ImageVector = download_done

    /** 勾选 */
    val Check: ImageVector = check

    /** 空仪表盘 */
    val EmptyDashboard: ImageVector = empty_dashboard

    /** 流程图 */
    val Flowchart: ImageVector = flowchart

    /** 字体下载 */
    val FontDownload: ImageVector = font_download

    /** 硬盘 */
    val HardDrive: ImageVector = hard_drive

    /** 移动数据箭头 */
    val MobiledataArrows: ImageVector = mobiledata_arrows

    /** 无线网络 */
    val NetworkWifi: ImageVector = network_wifi

    /** 包裹 */
    val Package2: ImageVector = package_2

    /** 以太网设置 */
    val SettingsEthernet: ImageVector = settings_ethernet

    /** 播放箭头（运行/执行） */
    val PlayArrow: ImageVector = play_arrow

    /** 添加（+） */
    val Add: ImageVector = add

    /** 删除 */
    val Delete: ImageVector = delete

    /** 刷新 */
    val Refresh: ImageVector = refresh

    /** 筛选列表 */
    val FilterList: ImageVector = filter_list

    /** 搜索 */
    val Search: ImageVector = search

    /** 编辑（铅笔） */
    val Edit: ImageVector = edit

    /** 终端2（指令） */
    val Terminal2: ImageVector = terminal_2

    /** 应用注册（可执行文件） */
    val AppRegistration: ImageVector = app_registration

    /** 搜索关闭 **/
    val SearchOff: ImageVector = search_off

    /** 连接 **/
    val Link: ImageVector = link

    /**关闭连接**/
    val LinkOff: ImageVector = link_off

    /**HTTP**/
    val Http: ImageVector = http

    /**CodeXml**/
    val CodeXml: ImageVector = code_xml

    /**AccountCircle**/
    val AccountCircle: ImageVector = account_circle

    /**Apps**/
    val Apps: ImageVector = apps

    /**Imagesmode**/
    val Imagesmode: ImageVector = imagesmode

    /**Wallpaper**/
    val Wallpaper: ImageVector = wallpaper

    /**Signal Cellular 3 Bar**/
    val SignalCellular3Bar = signal_cellular_3_bar
}
