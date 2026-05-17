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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
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

    @Value("${upload.delete-after-upload:false}")
    private boolean deleteAfterUpload;

    @Autowired
    private OSS ossClient;

    @PostConstruct
    public void init() {
        LOG.info("OssUploadService initialized. localDir={}, bucket={}", localAudioDir, bucketName);
    }

    @PreDestroy
    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }

    /**
     * 定时扫描本地音频目录，只上传 .wav 文件，上传成功后删除本地文件
     */
    @Scheduled(fixedRateString = "${upload.scan-interval:300000}")
    public void scanAndUpload() {
        Path audioDir = Paths.get(localAudioDir);
        if (!Files.exists(audioDir) || !Files.isDirectory(audioDir)) {
            LOG.warn("Audio directory does not exist: {}", localAudioDir);
            return;
        }

        List<Path> wavFiles;
        try (Stream<Path> stream = Files.list(audioDir)) {
            wavFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".wav"))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            LOG.error("Failed to list audio directory", e);
            return;
        }

        if (wavFiles.isEmpty()) {
            LOG.debug("No wav files found in {}", localAudioDir);
            return;
        }

        LOG.info("Found {} wav file(s) to upload", wavFiles.size());

        for (Path filePath : wavFiles) {
            String fileName = filePath.getFileName().toString();

            // 检查 OSS 上是否已存在（通过 segmentName 判断）
            String ossKey = "audio/" + fileName;
            if (ossClient.doesObjectExist(bucketName, ossKey)) {
                LOG.debug("Already exists in OSS: {}", fileName);
                // 已存在则直接删除本地文件
                deleteLocalFile(filePath);
                continue;
            }

            try {
                PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, ossKey, filePath.toFile());
                ossClient.putObject(putObjectRequest);

                String audioUrl = ossBaseUrl + "/" + ossKey;
                LOG.info("Uploaded to OSS: {} -> {}", fileName, audioUrl);

                // 根据配置决定是否删除本地文件
                if (deleteAfterUpload) {
                    deleteLocalFile(filePath);
                }
            } catch (Exception e) {
                LOG.error("Failed to upload audio file: {}", fileName, e);
            }
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
