package cn.wildfirechat.upload;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AudioUploadApplication {
    public static void main(String[] args) {
        SpringApplication.run(AudioUploadApplication.class, args);
    }
}
