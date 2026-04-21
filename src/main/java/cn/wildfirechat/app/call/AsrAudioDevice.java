package cn.wildfirechat.app.call;

import be.tarsos.dsp.resample.Resampler;
import cn.wildfirechat.AudioDevice;
import cn.wildfirechat.CallSession;
import cn.wildfirechat.app.ServiceImpl;
import cn.wildfirechat.app.entity.TranscriptionRecord;
import cn.wildfirechat.app.repository.TranscriptionRecordRepository;
import cn.wildfirechat.common.ErrorCode;
import cn.wildfirechat.pojos.Conversation;
import cn.wildfirechat.pojos.MessagePayload;
import cn.wildfirechat.pojos.SendMessageResult;
import cn.wildfirechat.sdk.RobotService;
import cn.wildfirechat.sdk.model.IMResult;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AsrAudioDevice implements AudioDevice {
    private static final Logger LOG = LoggerFactory.getLogger(AsrAudioDevice.class);
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("\\[(\\d+)\\+([\\d.]+)\\](.*)");
    private static final String SCREEN_SHARING_PREFIX = "screen_sharing_";
    private static final int WRITE_BATCH_SIZE = 50;
    private final List<TranscriptionRecord> writeBuffer = new ArrayList<>();
    private final Conversation conversation;
    private final String robotId;
    private final String conferenceId;
    private final RobotService robotService;
    private final TranscriptionRecordRepository transcriptionRecordRepository;
    private final String websocketUrl;
    private final Executor executor;
    private volatile CallSession callSession;

    private class Recorder implements Runnable {
        final private String userId;
        final private String realUserId;
        final private boolean screenSharing;
        Thread t;
        boolean stoped;
        private WebSocketClient webSocketClient;
        private LinkedBlockingQueue<byte[]> cacheQueue = new LinkedBlockingQueue<>(1000);
        private final Resampler resampler;
        private float[] floatIn = new float[4096];
        private float[] floatOut = new float[4096];
        private static final double RESAMPLE_FACTOR = 16000.0 / 48000.0;
        private final AtomicBoolean errorHandled = new AtomicBoolean(false);

        public Recorder(String userId) {
            this.userId = userId;
            if (userId != null && userId.startsWith(SCREEN_SHARING_PREFIX)) {
                this.screenSharing = true;
                this.realUserId = userId.substring(SCREEN_SHARING_PREFIX.length());
            } else {
                this.screenSharing = false;
                this.realUserId = userId;
            }
            // 中等品质、固定因子 1/3 的重采样器
            this.resampler = new Resampler(true, RESAMPLE_FACTOR, RESAMPLE_FACTOR);
        }

        private void handleWebsocketError() {
            if (errorHandled.compareAndSet(false, true)) {
                stoped = true;
                CallSession session = AsrAudioDevice.this.callSession;
                if (session != null) {
                    session.endCall();
                }
            }
        }

        public void start() {
            if(!stoped) {
                t = new Thread(this);
                stoped = false;
                t.start();
            }
        }

        public void stop() {
            stoped = true;
            flushWriteBuffer();
        }

        public void playoutData(byte[] sampleData, int nBuffSize) {
            // 1) int16 stereo -> int16 mono (48 kHz)
            final int GAIN = 1; // 增益倍数，可根据实际效果调整（10~50）
            int monoFrames48k = nBuffSize / 4; // 每帧 stereo 占 4 字节
            short[] monoSamples48k = new short[monoFrames48k];
            for (int i = 0, o = 0; i + 3 < nBuffSize; i += 4, o++) {
                short left = (short) ((sampleData[i] & 0xFF) | (sampleData[i + 1] << 8));
                short right = (short) ((sampleData[i + 2] & 0xFF) | (sampleData[i + 3] << 8));
                int mono = ((left + right) / 2) * GAIN;
                if (mono > 32767) mono = 32767;
                if (mono < -32768) mono = -32768;
                monoSamples48k[o] = (short) mono;
            }

            // 2) short[] -> float[] (归一化到 [-1.0, 1.0])
            if (floatIn.length < monoFrames48k) {
                floatIn = new float[monoFrames48k];
            }
            for (int i = 0; i < monoFrames48k; i++) {
                floatIn[i] = monoSamples48k[i] / 32768.0f;
            }

            // 3) 48 kHz -> 16 kHz 高品质重采样
            int expectedOut = (int) Math.ceil(monoFrames48k * RESAMPLE_FACTOR) + 10;
            if (floatOut.length < expectedOut) {
                floatOut = new float[expectedOut];
            }
            Resampler.Result result = resampler.process(
                    RESAMPLE_FACTOR,
                    floatIn, 0, monoFrames48k,
                    false,
                    floatOut, 0, expectedOut
            );
            int processed = result.outputSamplesGenerated;

            // 4) float[] -> short[] -> byte[] (16 kHz mono)
            byte[] monoData = new byte[processed * 2];
            for (int i = 0; i < processed; i++) {
                float sample = floatOut[i];
                if (sample > 1.0f) sample = 1.0f;
                if (sample < -1.0f) sample = -1.0f;
                short s = (short) (sample * 32767.0f);
                monoData[i * 2] = (byte) (s & 0xFF);
                monoData[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
            }
            // 限制队列最多保存约10秒数据（1000帧），超出时丢弃最旧数据
            if (!cacheQueue.offer(monoData)) {
                cacheQueue.poll();
                cacheQueue.offer(monoData);
            }
        }

        @Override
        public void run() {
            try {
                webSocketClient = new WebSocketClient(new URI(websocketUrl)) {
                    @Override
                    public void onOpen(ServerHandshake handshake) {
                        System.out.println("WebSocket connected");
                        webSocketClient.send(conferenceId + "--" + userId);
                    }

                    @Override
                    public void onMessage(String message) {
                        System.out.println("WebSocket received text: " + message);
                        if("pong".equals(message)) {
                            return; //ignore pong message
                        }

                        if (executor != null) {
                            executor.execute(() -> handleTranscriptionMessage(message));
                        } else {
                            handleTranscriptionMessage(message);
                        }
                    }

                    @Override
                    public void onClose(int code, String reason, boolean remote) {
                        System.out.println("WebSocket closed: " + reason);
                        if (!stoped) {
                            LOG.error("WebSocket unexpected close for userId={}, code={}, reason={}", userId, code, reason);
                            handleWebsocketError();
                        }
                    }

                    @Override
                    public void onError(Exception ex) {
                        ex.printStackTrace();
                        if (!stoped) {
                            LOG.error("WebSocket error for userId={}", userId, ex);
                            handleWebsocketError();
                        }
                    }
                };
                webSocketClient.connect();
                long connectStart = System.currentTimeMillis();
                while (!webSocketClient.isOpen() && !stoped) {
                    if (System.currentTimeMillis() - connectStart > 5000) {
                        LOG.error("WebSocket connection timeout for userId={}", userId);
                        handleWebsocketError();
                        return;
                    }
                    Thread.sleep(100);
                }

            } catch (InterruptedException | URISyntaxException e) {
                LOG.error("WebSocket setup error for userId={}", userId, e);
                handleWebsocketError();
                return;
            }

            long lastPingTime = System.currentTimeMillis();
            long lastFlushTime = System.currentTimeMillis();
            while (!stoped) {
                // 每15秒发送一次心跳
                if (System.currentTimeMillis() - lastPingTime > 15000) {
                    if (webSocketClient != null && webSocketClient.isOpen()) {
                        try {
                            webSocketClient.send("ping");
                        } catch (Exception e) {
                            LOG.error("Failed to send ping for userId={}", userId, e);
                            handleWebsocketError();
                            break;
                        }
                    }
                    lastPingTime = System.currentTimeMillis();
                }

                if (System.currentTimeMillis() - lastFlushTime > 2000) {
                    flushWriteBuffer();
                    lastFlushTime = System.currentTimeMillis();
                }

                byte[] data = cacheQueue.poll();
                if (data != null) {
                    if (webSocketClient != null && webSocketClient.isOpen()) {
                        webSocketClient.send(data);
                    }
                } else {
                    try {
                        Thread.sleep(2);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            flushWriteBuffer();
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            webSocketClient.close();
        }

        private void handleTranscriptionMessage(String message) {
            TranscriptionRecord record = new TranscriptionRecord();
            if (robotService != null && conversation != null) {
                try {
                    MessagePayload payload = new MessagePayload();
                    payload.setType(1);
                    payload.setSearchableContent(message);
                    if(message.contains("[") && message.contains("]")) {
                        payload.setSearchableContent(message.substring(message.indexOf("]")));
                    }
                    IMResult<SendMessageResult> result = robotService.sendMessage(robotId, conversation, payload);
                    if (result != null && result.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                        LOG.info("Send websocket text message success");
                        if (result.getResult() != null) {
                            record.setMessageId(result.getResult().getMessageUid());
                        }
                    } else {
                        LOG.error("Send websocket text message error {}", result != null ? result.getCode() : "null");
                    }
                } catch (Exception e) {
                    LOG.error("Send websocket text message exception", e);
                }
            }

            // 解析 [timestamp_ms+duration]text 格式
            if (transcriptionRecordRepository != null) {
                try {
                    Matcher matcher = MESSAGE_PATTERN.matcher(message);
                    if (matcher.matches()) {
                        long timestampMs = Long.parseLong(matcher.group(1));
                        double duration = Double.parseDouble(matcher.group(2));
                        String content = matcher.group(3);

                        record.setConferenceId(conferenceId);
                        record.setUserId(realUserId);
                        record.setTimestampMs(timestampMs);
                        record.setDuration((int)(duration*1000));
                        record.setContent(content);
                        record.setScreenSharing(screenSharing);
                        String segmentName = conferenceId + "--" + userId + "-[" + timestampMs + "+" + duration + "]";
                        record.setSegmentName(segmentName);
                        synchronized (writeBuffer) {
                            writeBuffer.add(record);
                            if (writeBuffer.size() >= WRITE_BATCH_SIZE) {
                                flushWriteBuffer();
                            }
                        }
                        LOG.info("Buffered transcription record for batch save, conferenceId={}, userId={}, screenSharing={}, timestampMs={}", conferenceId, realUserId, screenSharing, timestampMs);
                    } else {
                        LOG.warn("Received websocket text does not match expected pattern: {}", message);
                    }
                } catch (Exception e) {
                    LOG.error("Failed to save transcription record", e);
                }
            }
        }
    }

    private void flushWriteBuffer() {
        List<TranscriptionRecord> toSave;
        synchronized (writeBuffer) {
            if (writeBuffer.isEmpty()) {
                return;
            }
            toSave = new ArrayList<>(writeBuffer);
            writeBuffer.clear();
        }
        try {
            transcriptionRecordRepository.saveAll(toSave);
            LOG.info("Batch saved {} transcription records for conferenceId={}", toSave.size(), conferenceId);
        } catch (Exception e) {
            LOG.error("Failed to batch save transcription records for conferenceId={}, count={}", conferenceId, toSave.size(), e);
        }
    }

    private Map<String, Recorder> recorderMap = new ConcurrentHashMap<>();


    public AsrAudioDevice(Conversation conversation, String conferenceId, String robotId, RobotService robotService, TranscriptionRecordRepository transcriptionRecordRepository, String websocketUrl, Executor executor) {
        this.conversation = conversation;
        this.robotId = robotId;
        this.robotService = robotService;
        this.conferenceId = conferenceId;
        this.transcriptionRecordRepository = transcriptionRecordRepository;
        this.websocketUrl = websocketUrl;
        this.executor = executor;
    }

    @Override
    public int initPlayout(CallSession callSession, String userId) {
        this.callSession = callSession;
        Recorder recorder = recorderMap.computeIfAbsent(userId, s -> new Recorder(s));
        recorder.start();
        return 0;
    }

    @Override
    public int stopPlayout(CallSession callSession, String userId) {
        Recorder recorder = recorderMap.remove(userId);
        if(recorder != null) recorder.stop();
        return 0;
    }

    @Override
    public int initRecording(CallSession callSession) {
        return 0;
    }

    @Override
    public int startRecording(CallSession callSession) {
        return 0;
    }

    @Override
    public int stopRecording(CallSession callSession) {
        return 0;
    }

    @Override
    public void fetchRecordData(CallSession callSession, byte[] sampleData, int nSamples, int nSampleBytes, int nChannels, int nSampleRate, int nBuffSize) {
        //sampleData.length = 1920, nSamples = 480, nSampleBytes = 4, nChannels = 2, nSampleRate = 48000, nBuffSize = 1920);
    }

    @Override
    public void playoutData(CallSession callSession, String userId, byte[] sampleData, int nBuffSize) {
        Recorder recorder = recorderMap.get(userId);
        if(recorder != null) recorder.playoutData(sampleData, nBuffSize);
    }
}
