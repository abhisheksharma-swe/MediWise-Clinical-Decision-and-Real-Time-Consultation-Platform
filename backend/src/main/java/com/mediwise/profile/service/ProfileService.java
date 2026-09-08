package com.mediwise.profile.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.mediwise.auth.model.User;
import com.mediwise.common.exception.BusinessException;
import com.mediwise.profile.dto.UpdateProfileRequest;
import com.mediwise.profile.model.PatientProfile;
import com.mediwise.profile.repository.PatientProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final PatientProfileRepository profileRepository;
    private final AmazonS3 amazonS3;

    @Value("${application.aws.s3.bucket}")
    private String bucket;

    @Value("${application.aws.s3.base-url}")
    private String s3BaseUrl;

    public PatientProfile getOrCreateProfile(User user) {
        PatientProfile profile = profileRepository.findByUserId(user.getId())
                .orElseGet(() -> profileRepository.save(
                        PatientProfile.builder().userId(user.getId()).build()));
        profile.setPhone(user.getPhone());
        return profile;
    }

    @Transactional
    public PatientProfile updateProfile(User user, UpdateProfileRequest request) {
        PatientProfile profile = getOrCreateProfile(user);
        if (request.getFullName() != null) profile.setFullName(request.getFullName());
        if (request.getDob() != null) profile.setDob(request.getDob());
        if (request.getBloodType() != null) profile.setBloodType(request.getBloodType());
        if (request.getGender() != null) profile.setGender(request.getGender());
        if (request.getAddress() != null) profile.setAddress(request.getAddress());
        if (request.getEmergencyContact() != null) profile.setEmergencyContact(request.getEmergencyContact());
        PatientProfile saved = profileRepository.save(profile);
        saved.setPhone(user.getPhone());
        return saved;
    }

    private static final java.util.Set<String> ALLOWED_IMAGE_TYPES = java.util.Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    @Transactional
    public String uploadProfileImage(User user, MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("EMPTY_FILE", "File is empty");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException("INVALID_FILE_TYPE", "Only JPEG, PNG, WEBP, or GIF images are allowed");
        }

        String extension = switch (contentType.toLowerCase()) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> "";
        };

        String key = "profiles/" + user.getId() + "/" + UUID.randomUUID() + extension;

        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType);
            metadata.setContentLength(file.getSize());
            amazonS3.putObject(bucket, key, file.getInputStream(), metadata);
        } catch (IOException e) {
            throw new BusinessException("UPLOAD_FAILED", "Failed to upload image: " + e.getMessage());
        }

        String url = s3BaseUrl + "/" + key;
        PatientProfile profile = getOrCreateProfile(user);
        profile.setProfileImage(url);
        profileRepository.save(profile);

        log.info("Profile image uploaded for user {}: {}", user.getId(), url);
        return url;
    }
}
