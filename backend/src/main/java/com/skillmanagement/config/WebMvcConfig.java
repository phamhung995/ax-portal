package com.skillmanagement.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web MVC configuration for serving static uploads (e.g. avatars).
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:uploads/avatars/}")
    private String uploadDirPath;

    @Value("${app.upload.avatar-url-prefix:/uploads/avatars}")
    private String avatarUrlPrefix;

    @Value("${app.upload.skill-request-dir:uploads/skill-requests/}")
    private String skillRequestDirPath;

    @Value("${app.upload.skill-request-url-prefix:/uploads/skill-requests}")
    private String skillRequestUrlPrefix;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path path = Paths.get(uploadDirPath).toAbsolutePath().normalize();
        String prefix = avatarUrlPrefix.endsWith("/")
                ? avatarUrlPrefix.substring(0, avatarUrlPrefix.length() - 1)
                : avatarUrlPrefix;
        registry.addResourceHandler(prefix + "/**")
                .addResourceLocations("file:" + path.toString() + "/");

        Path requestPath = Paths.get(skillRequestDirPath).toAbsolutePath().normalize();
        String requestPrefix = skillRequestUrlPrefix.endsWith("/")
                ? skillRequestUrlPrefix.substring(0, skillRequestUrlPrefix.length() - 1)
                : skillRequestUrlPrefix;
        registry.addResourceHandler(requestPrefix + "/**")
                .addResourceLocations("file:" + requestPath.toString() + "/");
    }
}
