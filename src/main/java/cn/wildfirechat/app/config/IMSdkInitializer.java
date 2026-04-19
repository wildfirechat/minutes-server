package cn.wildfirechat.app.config;

import cn.wildfirechat.sdk.AdminConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class IMSdkInitializer implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(IMSdkInitializer.class);

    @Autowired
    private IMServerConfig imServerConfig;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        LOG.info("Initializing Wildfire IM SDK...");
        LOG.info("IM Server Admin URL: {}", imServerConfig.getAdminUrl());

        AdminConfig.initAdmin(imServerConfig.getAdminUrl(), imServerConfig.getAdminSecret());

        LOG.info("Wildfire IM SDK initialized successfully");
    }
}
