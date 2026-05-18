# upload2oss

ASR 音频文件自动上传 OSS 工具。

## 功能

- 定时扫描本地音频目录，只上传 `.wav` 后缀的文件
- 通过 OSS `doesObjectExist` 接口避免重复上传
- 支持配置上传成功后是否删除本地文件（默认不删除）
- 支持配置是否转码为 mp3 后上传（默认不转码）
- 默认每 **30 秒** 扫描一次

## 使用场景

本项目用于把 ASR 服务生成的音频文件自动上传到阿里云 OSS，供 Web 页面播放。

> 如果音频文件只需要在内网访问，也可以考虑使用 **Nginx 静态资源服务** 方案，详见项目根目录 `docs/audio-playback.md`。

## 目录结构

```
upload2oss/
├── pom.xml
├── README.md
└── src/main/
    ├── java/cn/wildfirechat/upload/
    │   ├── AudioUploadApplication.java
    │   ├── config/OssConfig.java
    │   └── service/OssUploadService.java
    └── resources/application.properties
```

## 配置

修改 `src/main/resources/application.properties`：

```properties
# OSS 配置
oss.endpoint=oss-cn-hangzhou.aliyuncs.com
oss.bucket=your-audio-bucket
oss.access-key-id=your-access-key-id
oss.access-key-secret=your-access-key-secret
oss.base-url=https://your-audio-bucket.oss-cn-hangzhou.aliyuncs.com

# 本地音频文件目录（ASR 输出目录）
audio.local.dir=/data/asr/audio

# 转码配置
audio.transcode.enabled=false
audio.transcode.format=mp3

# 扫描间隔（毫秒），默认 30 秒
upload.scan-interval=30000

# 上传成功后是否删除本地文件，默认 false
upload.delete-after-upload=false
```

## 运行

```bash
# 编译
cd upload2oss
mvn package -DskipTests

# 运行
java -jar target/upload2oss-0.1.jar
```

## 注意事项

- 该工具**不连接数据库**，仅负责文件上传
- 数据库中只保存 `segment_name`（文件名），`audioUrl` 由主项目通过 `audio.base.url` 配置与 `segmentName` 动态拼接
- 首次运行时会自动上传目录下所有历史 `.wav` 文件
- 建议在生产环境配合 OSS 生命周期规则和 CDN 使用

### 转码功能

如需开启转码（`audio.transcode.enabled=true`），**服务器必须已安装 ffmpeg**。

安装方式（CentOS / Ubuntu）：

```bash
# CentOS
sudo yum install ffmpeg ffmpeg-devel

# Ubuntu
sudo apt-get install ffmpeg
```

开启转码后，系统会调用 `ffmpeg -i input.wav -y output.mp3` 将 wav 转成 mp3 再上传。此时需要同步将主项目 `config/application.properties` 中的 `audio.file.extension` 改为 `mp3`，确保后端拼接的 URL 后缀与 OSS 上的实际文件格式一致。
