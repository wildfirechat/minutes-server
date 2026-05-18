package cn.wildfirechat.upload.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.PutObjectRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class OssUploadService {
    private static final Logger LOG = LoggerFactory.getLogger(OssUploadService.class);

    @Value("${oss.bucket}")
    private String bucketName;

    @Value("${oss.base-url}")
    private String ossBaseUrl;

    @Value("${audio.local.dir}")
    private String localAudioDir;

    @Value("${audio.transcode.enabled:false}")
    private boolean transcodeEnabled;

    @Value("${audio.transcode.format:mp3}")
    private String transcodeFormat;

    @Value("${upload.delete-after-upload:false}")
    private boolean deleteAfterUpload;

    @Autowired
    private OSS ossClient;

    @PostConstruct
    public void init() {
        LOG.info("OssUploadService initialized. localDir={}, bucket={}, transcodeEnabled={}, transcodeFormat={}",
                localAudioDir, bucketName, transcodeEnabled, transcodeFormat);
    }

    @PreDestroy
    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }

    /**
     * 定时扫描本地音频目录，只上传指定后缀文件。
     * 若开启转码，先调用 ffmpeg 转成目标格式后再上传。
     */
    @Scheduled(fixedRateString = "${upload.scan-interval:300000}")
    public void scanAndUpload() {
        Path audioDir = Paths.get(localAudioDir);
        if (!Files.exists(audioDir) || !Files.isDirectory(audioDir)) {
            LOG.warn("Audio directory does not exist: {}", localAudioDir);
            return;
        }

        String sourceExt = ".wav";
        List<Path> sourceFiles;
        try (Stream<Path> stream = Files.list(audioDir)) {
            sourceFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(sourceExt))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            LOG.error("Failed to list audio directory", e);
            return;
        }

        if (sourceFiles.isEmpty()) {
            LOG.debug("No wav files found in {}", localAudioDir);
            return;
        }

        LOG.info("Found {} wav file(s) to upload", sourceFiles.size());

        for (Path sourcePath : sourceFiles) {
            String sourceFileName = sourcePath.getFileName().toString();
            String targetFileName = transcodeEnabled
                    ? sourceFileName.substring(0, sourceFileName.length() - sourceExt.length()) + "." + transcodeFormat.toLowerCase()
                    : sourceFileName;
            Path targetPath = transcodeEnabled ? sourcePath.resolveSibling(targetFileName) : sourcePath;

            String ossKey = "audio/" + targetFileName;

            // 检查 OSS 上是否已存在（通过目标文件名判断）
            if (ossClient.doesObjectExist(bucketName, ossKey)) {
                LOG.debug("Already exists in OSS: {}", targetFileName);
                // 已存在则删除本地源文件及可能的转码残留文件
                deleteLocalFile(sourcePath);
                if (transcodeEnabled) {
                    deleteLocalFile(targetPath);
                }
                continue;
            }

            // 若开启转码，先执行 ffmpeg
            if (transcodeEnabled) {
                boolean transcoded = transcode(sourcePath, targetPath);
                if (!transcoded) {
                    LOG.error("Skip upload due to transcode failure: {}", sourceFileName);
                    continue;
                }
            }

            try {
                File uploadFile = targetPath.toFile();
                if (!uploadFile.exists()) {
                    LOG.error("Upload file does not exist: {}", targetFileName);
                    continue;
                }

                PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, ossKey, uploadFile);
                ossClient.putObject(putObjectRequest);

                String audioUrl = ossBaseUrl + "/" + ossKey;
                LOG.info("Uploaded to OSS: {} -> {}", targetFileName, audioUrl);

                // 根据配置决定是否删除本地文件
                if (deleteAfterUpload) {
                    deleteLocalFile(sourcePath);
                    if (transcodeEnabled) {
                        deleteLocalFile(targetPath);
                    }
                }
            } catch (Exception e) {
                LOG.error("Failed to upload audio file: {}", targetFileName, e);
            }
        }
    }

    /**
     * 调用 ffmpeg 将源文件转码为目标格式
     */
    private boolean transcode(Path sourcePath, Path targetPath) {
        String targetFileName = targetPath.getFileName().toString();
        try {
            if (Files.exists(targetPath)) {
                LOG.debug("Target file already exists, skip transcode: {}", targetFileName);
                return true;
            }

            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-i", sourcePath.toString(),
                    "-y",
                    targetPath.toString()
            );
            pb.redirectErrorStream(true);
            pb.inheritIO();
            Process process = pb.start();

            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                LOG.error("Transcode timeout for file: {}", sourcePath.getFileName().toString());
                process.destroyForcibly();
                return false;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                LOG.error("Transcode failed with exit code {}: {}", exitCode, sourcePath.getFileName().toString());
                return false;
            }

            LOG.info("Transcoded: {} -> {}", sourcePath.getFileName().toString(), targetFileName);
            return true;
        } catch (Exception e) {
            LOG.error("Transcode exception for file: {}", sourcePath.getFileName().toString(), e);
            return false;
        }
    }

    private void deleteLocalFile(Path filePath) {
        try {
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                LOG.info("Deleted local file: {}", filePath.getFileName().toString());
            }
        } catch (Exception e) {
            LOG.error("Failed to delete local file: {}", filePath.getFileName().toString(), e);
        }
    }
}
