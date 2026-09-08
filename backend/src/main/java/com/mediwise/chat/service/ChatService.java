package com.mediwise.chat.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.mediwise.chat.dto.ChatMediaUploadResponse;
import com.mediwise.chat.dto.SendMessageRequest;
import com.mediwise.chat.model.ChatMessage;
import com.mediwise.chat.repository.ChatMessageRepository;
import com.mediwise.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository messageRepository;
    private final AmazonS3 amazonS3;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${application.aws.s3.bucket}")
    private String bucket;

    @Value("${application.aws.s3.base-url}")
    private String s3BaseUrl;

    private static final int RECENT_CACHE_SIZE = 50;
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    // Matches application.yml's spring.servlet.multipart.max-file-size (10MB) - a request
    // over that is already rejected by Spring before this check ever runs, so this just
    // gives a clean, typed BusinessException for anything up to that same ceiling.
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final Set<String> FILE_TYPES = Set.of(
            "application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain"
    );

    /** Uploads a chat attachment to S3 and classifies it as IMAGE or FILE — the caller (ChatController) has already verified room participancy before this is reached. */
    public ChatMediaUploadResponse uploadMedia(String roomId, String uploaderId, MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("EMPTY_FILE", "File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException("FILE_TOO_LARGE", "Attachments must be 15MB or smaller");
        }

        String contentType = file.getContentType();
        ChatMessage.ContentType category;
        if (contentType != null && IMAGE_TYPES.contains(contentType.toLowerCase())) {
            category = ChatMessage.ContentType.IMAGE;
        } else if (contentType != null && FILE_TYPES.contains(contentType.toLowerCase())) {
            category = ChatMessage.ContentType.FILE;
        } else {
            throw new BusinessException("INVALID_FILE_TYPE",
                    "Only images (JPEG/PNG/WEBP/GIF), PDF, Word documents, or plain text files are allowed");
        }

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "attachment";
        String extension = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf('.')) : "";
        String key = "chat/" + roomId + "/" + uploaderId + "/" + UUID.randomUUID() + extension;

        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType);
            metadata.setContentLength(file.getSize());
            amazonS3.putObject(bucket, key, file.getInputStream(), metadata);
        } catch (IOException e) {
            throw new BusinessException("UPLOAD_FAILED", "Failed to upload attachment: " + e.getMessage());
        }

        String url = s3BaseUrl + "/" + key;
        log.info("Chat attachment uploaded to room {} by {}: {}", roomId, uploaderId, url);
        return ChatMediaUploadResponse.builder()
                .url(url)
                .contentType(category)
                .originalFilename(originalName)
                .build();
    }

    public ChatMessage saveMessage(SendMessageRequest request, String senderId, String senderRole) {
        ChatMessage message = ChatMessage.builder()
                .roomId(request.getRoomId())
                .senderId(senderId)
                .senderRole(senderRole)
                .content(request.getContent())
                .contentType(request.getContentType())
                .mediaUrl(request.getMediaUrl())
                .read(false)
                .deleted(false)
                .build();

        message = messageRepository.save(message);

        // Invalidate cache for this room
        if (redisTemplate != null) {
            redisTemplate.delete("chat_recent:" + request.getRoomId());
        }

        log.debug("Message saved to room {}", request.getRoomId());
        return message;
    }

    public Page<ChatMessage> getMessages(String roomId, int page, int size) {
        return messageRepository.findByRoomIdAndDeletedFalseOrderBySentAtDesc(
                roomId, PageRequest.of(page, size, Sort.by("sentAt").descending()));
    }

    public void markAsRead(String roomId, String readerId) {
        // Mark unread messages from the other party as read
        messageRepository.findByRoomIdAndDeletedFalseOrderBySentAtDesc(
                roomId, PageRequest.of(0, RECENT_CACHE_SIZE))
                .getContent()
                .stream()
                .filter(m -> !m.getSenderId().equals(readerId) && !m.isRead())
                .forEach(m -> {
                    m.setRead(true);
                    m.setReadAt(java.time.Instant.now());
                    messageRepository.save(m);
                });
        if (redisTemplate != null) {
            redisTemplate.delete("chat_recent:" + roomId);
        }
    }
}
