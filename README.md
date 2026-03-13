# BatteryRecorder

## 介绍

一个电池功率记录 App，旨在使用更低的 CPU 开销来记录更精确的功率数据，并为用户提供较为精准的续航预测。

## 功能

- 精细化记录调参，更切合你的需求
- 原始、趋势功耗图，各种需求都能覆盖
- 自定义曲线隐藏
- 息屏功耗记录，探索🧐未知场景

## 使用文档
- [文档](https://itosang.github.io/BatteryRecorder/)


## ToDo

### app

- [x] adb 启动 用户引导
- [x] 分 app 续航预测
- [x] 分场景预测续航
- [x] 曲线放大缩小
- [x] 临时隐藏某条曲线
- [x] BOOT_COMPLETED 自启动

### server

- [x] 解决 Monitor 唤醒锁异常(实际为 callback 阻塞)
- [ ] 监听 app 安装，并在适当时机重启 Server
- [ ] 重启 server 时，续接之前 server 的状态
- [x] 额外电压记录
- [x] 电池温度信息 `/sys/class/power_supply/battery/temp` 记录

### ext

- [ ] 开机功耗曲线
- [ ] ~~充电复位~~

## 下载

- [GitHub Releases](https://github.com/Itosang/BatteryRecorder/releases)
- [GitHub Actions](https://github.com/Itosang/BatteryRecorder/actions)

## 反馈

- [QQ 群](https://qm.qq.com/q/6q5etoYAuc)
- [GitHub Issues](https://github.com/Itosang/BatteryRecorder/issues) (推荐)