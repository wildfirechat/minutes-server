# 会议音频回放方案说明

## 背景

本项目的音频转写功能由 ASR（自动语音识别）服务完成。会议进行过程中，音频数据通过 WebSocket 实时发送到 ASR 服务（配置项 `asr.ws.url`），ASR 服务在完成转写的同时，会把原始音频片段保存到本地文件系统中。

音频文件名格式为：

```
{conferenceId}--{userId}-[{timestampMs}+{duration}]
```

例如：`conf123--user456-[1715901234567+5.23]`

Web 前端播放音频时，通过 `audio.base.url` 配置项拼接完整的访问地址：

```
{audioBaseUrl}/{segmentName}
```

> 当前 `audio.base.url` 为空，因此前端无法获取音频播放地址。需要根据实际情况选择以下两种方案之一进行部署。

---

## 方案一：Nginx 静态资源服务（推荐）

### 适用场景

- 音频文件保存在 ASR 服务器本地磁盘
- 内网环境或对数据不出境有要求
- 希望低成本、快速部署

### 原理

在 ASR 服务器（或挂载了相同存储的其他服务器）上部署 Nginx，将音频文件目录映射为 HTTP 访问路径。浏览器通过 Nginx 直接访问音频文件，Nginx 原生支持 HTTP `Range` 请求，可实现边下边播和进度条拖动。

### 部署步骤

#### 1. 确认音频文件目录和格式

登录 ASR 服务器，确认音频文件存放目录，例如：

```bash
ls /data/asr/audio/
# 输出示例：conf123--user456-[1715901234567+5.23].wav
```

> **注意**：ASR 服务已保存为 `.wav` 格式，浏览器可直接播放。

#### 2. 安装 Nginx

```bash
# CentOS / RHEL
sudo yum install nginx

# Ubuntu / Debian
sudo apt-get install nginx
```

#### 3. 配置 Nginx

编辑 Nginx 配置文件（通常位于 `/etc/nginx/nginx.conf` 或 `/etc/nginx/conf.d/` 目录下），新增一个 server 配置块：

```nginx
server {
    listen 80;
    server_name 101.42.4.222;  # 替换为实际域名或 IP

    # 音频文件访问路径
    location /audio/ {
        # 音频文件在服务器上的实际目录
        alias /data/asr/audio/;

        # 允许跨域访问（如果 Web 页面和音频服务域名不同）
        add_header Access-Control-Allow-Origin * always;
        add_header Access-Control-Allow-Methods 'GET, OPTIONS' always;
        add_header Access-Control-Allow-Headers 'Range' always;

        # 开启 Range 请求支持，实现边下边播
        add_header Accept-Ranges bytes;

        # 缓存控制（可选）
        expires 7d;
        add_header Cache-Control "public, immutable";

        # 处理预检请求
        if ($request_method = 'OPTIONS') {
            add_header Access-Control-Max-Age 1728000;
            add_header Content-Length 0;
            return 204;
        }
    }
}
```

> **MIME 类型说明**：Nginx 默认会根据文件扩展名自动设置 `Content-Type`。如果音频文件没有扩展名，或扩展名不是标准格式，可以在 `nginx.conf` 的 `http` 块中手动添加：
>
> ```nginx
> types {
>     audio/wav   wav;
>     audio/mpeg  mp3;
>     audio/mp4   m4a;
>     audio/ogg   ogg;
>     audio/x-pcm pcm;
> }
> ```

#### 4. 测试配置并重启 Nginx

```bash
sudo nginx -t
sudo systemctl restart nginx
```

#### 5. 验证访问

```bash
# 测试直接访问
curl -I http://asr-audio.yourdomain.com/audio/conf123--user456-%5B1715901234567%2B5.23%5D.wav

# 测试 Range 请求（应返回 206 Partial Content）
curl -H "Range: bytes=0-1023" http://asr-audio.yourdomain.com/audio/conf123--user456-%5B1715901234567%2B5.23%5D.wav
```

#### 6. 修改项目配置

在 `config/application.properties` 中配置：

```properties
audio.base.url=http://asr-audio.yourdomain.com/audio
```

重新打包部署后，Web 页面即可正常播放音频。

---

## 方案二：OSS 对象存储（适合大规模 / 公网访问）

### 适用场景

- 需要公网访问音频文件
- ASR 服务器带宽有限，希望通过 CDN 加速
- 数据归档和长期保存需求

### 原理

将 ASR 服务器上的音频文件定期或实时上传到对象存储（如阿里云 OSS、AWS S3、腾讯云 COS 等），Web 前端直接访问 OSS 提供的 URL 播放音频。OSS 天然支持 HTTP `Range` 请求和 CDN 加速。

### 整体架构

```
ASR 服务器本地文件  →  扫描上传程序  →  OSS  →  CDN（可选） →  Web 前端播放
```

### 部署步骤

#### 1. 创建 OSS Bucket

以阿里云 OSS 为例：

1. 登录阿里云控制台，进入 OSS 服务
2. 创建 Bucket，选择区域（建议与服务器同区域以降低上传延迟）
3. 开启 **公共读** 权限（或配置 Referer 防盗链）
4. （可选）绑定自定义域名并开启 CDN 加速

#### 2. 获取访问密钥

在阿里云 RAM 控制台创建子账号，授予 `AliyunOSSFullAccess` 权限，获取：

- `AccessKeyId`
- `AccessKeySecret`

#### 3. 自动扫描上传程序

部署在 ASR 服务器上，定期扫描本地音频目录，将新文件上传到 OSS。

##### 3.1 使用 upload2oss 工具

项目中已提供独立的 `upload2oss` 上传工具，位于项目根目录的 `upload2oss/` 文件夹下。该工具是一个独立的 Spring Boot 应用，专用于扫描 ASR 本地音频目录并自动上传到 OSS。

核心逻辑：
- 只扫描 `.wav` 后缀的文件
- 默认每 **30 秒** 自动扫描一次
- 通过 OSS `doesObjectExist` 接口避免重复上传
- 支持配置上传成功后是否删除本地文件（默认不删除）
- **不连接数据库**，仅负责文件上传

具体配置和使用方法请参考 [`upload2oss/README.md`](../upload2oss/README.md)。

#### 4. 修改项目配置

上传成功后，在 `config/application.properties` 中配置 OSS 访问地址：

```properties
audio.base.url=https://your-audio-bucket.oss-cn-hangzhou.aliyuncs.com/audio
```

#### 5. OSS 访问优化（可选）

##### 5.1 开启 CDN 加速

在阿里云 CDN 控制台：
1. 添加加速域名（如 `audio.yourdomain.com`）
2. 源站选择 OSS 域名
3. 配置 HTTPS 证书
4. 修改 `audio.base.url` 为 CDN 域名：

```properties
audio.base.url=https://audio.yourdomain.com/audio
```

##### 5.2 配置 Referer 防盗链

在 OSS Bucket 的 **权限管理 → 防盗链** 中：
- 开启 Referer 白名单
- 添加 Web 页面的域名（如 `https://minutes.yourdomain.com`）
- 勾选 **允许空 Referer**（根据实际需求决定）

##### 5.3 配置生命周期规则

如果音频文件只需要保留一段时间（如 30 天），可以在 OSS 控制台配置生命周期规则，到期后自动转存为低频存储或删除。

---

## 方案对比

| 维度 | Nginx 静态资源 | OSS 对象存储 |
|------|---------------|-------------|
| **部署成本** | 低（已有服务器） | 中（按流量/存储付费） |
| **访问速度** | 受服务器带宽限制 | CDN 加速，全球就近访问 |
| **公网访问** | 需要服务器有公网 IP | 天然支持公网访问 |
| **扩展性** | 单机，扩容需加硬盘/服务器 | 无限扩展 |
| **可靠性** | 依赖单台服务器 | 多副本冗余，SLA 保障 |
| **维护成本** | 低 | 中（需要上传程序和费用管理） |
| **Range 支持** | Nginx 原生支持 | OSS 原生支持 |
| **推荐场景** | 内网、小范围使用 | 公网、大规模、长期保存 |

---

## 常见问题

### Q1: 音频文件很多，磁盘满了怎么办？

- **Nginx 方案**：定期清理旧文件（如保留最近 7 天），或配置日志轮转。
- **OSS 方案**：上传工具支持配置 `upload.delete-after-upload=true` 来自动删除本地文件，默认不删除。


