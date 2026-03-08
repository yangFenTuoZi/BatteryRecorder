# BatteryRecorder

## 介绍

一个电池功率记录 App，旨在使用更低的 CPU 开销来记录更精确的功率数据，并为用户提供较为精准的续航预测。

## ToDo

### app

- [x] adb 启动 用户引导
- [ ] 分 app/场景统计功耗
- [x] 分场景预测续航
- [x] 曲线放大缩小
- [ ] 临时隐藏某条曲线
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

- [GitHub Releases](https://github.com/Itosang/BatteryRecorder/releases) (暂无)
- [GitHub Actions](https://github.com/Itosang/BatteryRecorder/actions)

## 反馈

- [QQ 群](https://qm.qq.com/q/6q5etoYAuc) (推荐)
- [GitHub Issues](https://github.com/Itosang/BatteryRecorder/issues)
