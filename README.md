# 野火会议纪要服务

AI 会议助手，实时监听会议发言并转写为文字，会议结束后自动整理结构化纪要并推送给参会者。

## 功能特性

1. **实时语音转写**：机器人加入音视频会议后，实时采集音频并调用 ASR 服务转换为文字，以实时字幕形式发送到会议群聊
2. **文字智能矫正**：会议结束后，自动调用大模型对语音识别文本进行逐条矫正（修正同音字、错别字、专有名词、标点等），矫正结果保存到数据库
3. **会议纪要整理**：基于矫正后的发言文本，调用大模型自动生成结构化的会议纪要（会议要点、待办事项、关键决策）
4. **纪要私聊推送**：整理完成后，自动通过私聊将会议纪要发送给每位参会者
5. **会议语音重放**：支持会议内语音重放功能

## 依赖
- 音视频高级版，支持会议功能
- 语音转文字服务
- 大模型服务（兼容OpenAI接口）

## 编译

需要分平台打包，只支持 linux x64 和 mac arm64 平台，分别如下：

```bash
mvn -Djavacpp.platform=linux-x86_64 -Dmaven.test.skip=true package
mvn -Djavacpp.platform=macosx-arm64 -Dmaven.test.skip=true package
```

## 创建会议纪要机器人

在 IM 服务数据库中，执行如下 SQL：

```sql
insert into t_user (`_uid`,`_name`,`_display_name`,`_portrait`,`_type`,`_dt`) values ('robotminutes','robotminutes','AI会议助手','https://static.wildfirechat.cn/botconference.png',1,1);
insert into t_robot (`_uid`,`_owner`,`_secret`,`_callback`,`_state`,`_dt`) values ('robotminutes', 'robotminutes', '123456', 'http://127.0.0.1:8883/robot/recvmsg', 0, 1);
```

## 配置文件

本服务有配置文件在工程的 `config` 目录下，分别是 `application.properties`。请正确配置并放到 jar 包所在目录下的 `config` 目录下。

### 核心配置项说明

```properties
# 服务端口
server.port=8883

# MySQL 数据库（生产环境）
spring.datasource.url=jdbc:mysql://localhost:3306/robot_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=your_password

# JPA 自动建表/更新表结构
spring.jpa.hibernate.ddl-auto=update

# IM 管理后台配置
im.server.admin_url=http://localhost:18080
im.server.admin_secret=123456

# ASR WebSocket 地址（语音识别服务）
asr.ws.url=ws://192.168.1.81:12436

# 机器人身份配置
robot.im_id=robotminutes
robot.im_url=http://localhost
robot.im_secret=123456

# 大模型配置（兼容 OpenAI 格式，默认使用 Moonshot/Kimi）
llm.api.url=https://api.moonshot.cn/v1/chat/completions
llm.api.key=sk-xxxxxxxxxxxxxxxx
llm.api.model=kimi-k2-turbo-preview
```

> **注意**：`llm.api.url`、`llm.api.key`、`llm.api.model` 用于文字矫正和会议纪要整理。支持任何兼容 OpenAI `/v1/chat/completions` 接口的大模型服务（如通义千问、文心一言、ChatGLM、OpenAI 等）。

## 运行

在 `target` 目录找到 `minutes-XXXX.jar`，把 jar 包和放置配置文件的 `config` 目录放到一起，然后执行下面命令：

```bash
java -jar minutes-XXXXX.jar
```

## 使用流程

1. 将会议纪要机器人（`robotminutes`）拉入音视频会议
2. 机器人自动加入会议，开始实时语音识别并发送字幕到群聊
3. 会议过程中，所有发言记录和参会者信息自动保存到数据库
4. 会议结束后，机器人自动执行：
   - **文字矫正**：读取全部原始转录文本，调用大模型逐条矫正，保存 `corrected_content`
   - **纪要整理**：基于矫正后的文本，调用大模型生成结构化会议纪要
   - **私聊推送**：将会议纪要私聊发送给每位参会者

## 数据库表说明

服务启动时会通过 JPA 自动创建/更新以下数据表：

| 表名 | 说明 |
|---|---|
| `transcription_record` | 语音转写记录，包含原始文本 `content` 和矫正后文本 `corrected_content` |
| `conference_participant` | 会议参会者记录 |
| `meeting_summary` | 会议纪要表 |

## 使用到的开源代码

1. [TypeBuilder](https://github.com/ikidou/TypeBuilder) 一个用于生成泛型的简易 Builder

## LICENSE

UNDER MIT LICENSE. 详情见 LICENSE 文件
