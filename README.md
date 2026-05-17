# Minutes Server

会议记录服务端项目，包含后端 API 和 Web 管理页面。

## 项目结构

```
minutes-server/
├── config/
│   └── application.properties          # 后端配置文件
├── docs/
│   └── audio-playback.md               # 音频回放方案文档
├── src/
│   ├── lib/                            # 本地依赖 jar
│   └── main/
│       ├── java/                       # Java 后端代码
│       └── resources/static/           # Web 前端构建产物
├── upload2oss/                         # ASR 音频上传 OSS 工具
│   └── README.md
├── web/                                # Vue 3 前端源码
├── pom.xml
└── README.md
```

## 技术栈

| 端 | 技术 |
|----|------|
| 后端 | Spring Boot 2.2 + JPA + MySQL |
| 前端 | Vue 3 + Vite + Element Plus + Pinia |
| 通信 | WildfireChat SDK |

## 快速开始

### 后端

```bash
# 编译运行
mvn spring-boot:run

# 或打包后运行
mvn clean -Djavacpp.platform=linux-x86_64 package -DskipTests
java -jar target/minutes-server-0.1.jar
```
> 只支持linux x86_64和maxos arm64这2个平台，其他平台不支持。

### 前端

```bash
cd web
npm install
npm run dev        # 开发
npm run build      # 构建（产物输出到 src/main/resources/static）
```

## 主要功能

- 会议实时转写与录音
- 会议纪要自动生成
- Web 端会议详情查看（总结、参会者、转写记录、音频回放）
- 登录认证（通过 Native JSSDK 获取授权码）

## 相关文档

- [音频回放方案](docs/audio-playback.md) — 介绍 Nginx 和 OSS 两种音频文件访问方案
- [upload2oss 工具](upload2oss/README.md) — ASR 音频自动上传 OSS
