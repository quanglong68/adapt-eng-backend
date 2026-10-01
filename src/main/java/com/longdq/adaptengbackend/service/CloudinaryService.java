package com.longdq.adaptengbackend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public CloudinaryService(
            @Value("${cloudinary.cloud-name}") String cloudName,
            @Value("${cloudinary.api-key}") String apiKey,
            @Value("${cloudinary.api-secret}") String apiSecret) {
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true));
    }

    public String uploadImage(byte[] imageBytes, String folderName) {
        try {
            Map<?, ?> uploadResult = cloudinary.uploader().upload(imageBytes, ObjectUtils.asMap(
                    "folder", folderName,
                    "resource_type", "image"
            ));
            return uploadResult.get("secure_url").toString();
        } catch (Exception e) {
            log.error("Lỗi khi upload ảnh lên Cloudinary: {}", e.getMessage());
            throw new RuntimeException("Upload Cloudinary thất bại");
        }
    }
}