package cn.wildfirechat.app.config;

import cn.wildfirechat.sdk.RobotService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RobotServiceConfig {

    @Value("${robot.im_url}")
    private String robotImUrl;

    @Value("${robot.im_id}")
    private String robotImId;

    @Value("${robot.im_secret}")
    private String robotImSecret;

    @Bean
    public RobotService robotService() {
        return new RobotService(robotImUrl, robotImId, robotImSecret);
    }
}
