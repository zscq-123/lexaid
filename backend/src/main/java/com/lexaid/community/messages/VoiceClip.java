package com.lexaid.community.messages;

import com.lexaid.common.BizException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** Validated audio bytes for a voice message. Only the container magic is trusted. */
public record VoiceClip(String fileName, String mediaType, byte[] bytes) {

    private static final long MAX_BYTES = 20L * 1024 * 1024;

    public static VoiceClip read(MultipartFile upload) {
        if (upload == null || upload.isEmpty() || upload.getSize() <= 0) {
            throw new BizException("请选择或录制语音");
        }
        if (upload.getSize() > MAX_BYTES) throw new BizException("语音消息不能超过 20 MB");
        byte[] bytes;
        try {
            bytes = upload.getBytes();
        } catch (IOException e) {
            throw new BizException("语音读取失败，请重新录制");
        }
        String[] format = detect(bytes);
        return new VoiceClip("语音." + format[1], format[0], bytes);
    }

    private static String[] detect(byte[] bytes) {
        if (startsWith(bytes, new byte[]{'R', 'I', 'F', 'F'}) && bytes.length >= 12
                && bytes[8] == 'W' && bytes[9] == 'A' && bytes[10] == 'V' && bytes[11] == 'E') {
            return new String[]{"audio/wav", "wav"};
        }
        if (startsWith(bytes, new byte[]{'O', 'g', 'g', 'S'})) {
            return new String[]{"audio/ogg", "ogg"};
        }
        if (startsWith(bytes, new byte[]{0x1a, 0x45, (byte) 0xdf, (byte) 0xa3})) {
            return new String[]{"audio/webm", "webm"};
        }
        if (bytes.length >= 12 && bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p') {
            return new String[]{"audio/mp4", "m4a"};
        }
        if (startsWith(bytes, new byte[]{'I', 'D', '3'})
                || (bytes.length >= 2 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xe0) == 0xe0)) {
            return new String[]{"audio/mpeg", "mp3"};
        }
        throw new BizException("语音格式不支持，请使用浏览器录音或上传 WAV/OGG/WebM/M4A/MP3");
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) return false;
        }
        return true;
    }
}
