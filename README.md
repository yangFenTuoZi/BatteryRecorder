# BatteryRecorder

## 介绍

一个电池功率记录 App，旨在使用更低的 CPU 开销来记录更精确的功率数据，并为用户提供较为精准的续航预测。

## ToDo

### app

- [ ] adb 启动 用户引导
- [ ] 分 app/场景统计功耗
- [ ] 分场景预测续航
- [x] ~~曲线放大缩小~~

### server

- [ ] 解决 Monitor 唤醒锁异常
- [ ] Magisk 模块开机自启
- [ ] 监听 app 安装，并在适当时机重启 Server
- [ ] 重启 server 时，续接之前 server 的状态
- [ ] 电池温度信息 `/sys/class/power_supply/battery/temp` 记录

## 下载

- [GitHub Releases](https://github.com/yangFenTuoZi/BatteryRecorder/releases) (暂无)
- [GitHub Actions](https://github.com/yangFenTuoZi/BatteryRecorder/actions)

## 反馈

- [QQ 群](https://qm.qq.com/q/6q5etoYAuc) (推荐)
- [GitHub Issues](https://github.com/yangFenTuoZi/BatteryRecorder/issues)