package com.mediwise.chat.dto;

import com.mediwise.chat.model.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMediaUploadResponse {
    private String url;
    private ChatMessage.ContentType contentType;
    private String originalFilename;
}
