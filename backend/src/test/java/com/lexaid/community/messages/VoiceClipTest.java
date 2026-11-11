package com.lexaid.community.messages;

import com.lexaid.common.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class VoiceClipTest {

    @Test
    void acceptsBrowserWebmAndWavContainers() {
        var webm = new MockMultipartFile("file", "voice.webm", "audio/webm",
                new byte[]{0x1a, 0x45, (byte) 0xdf, (byte) 0xa3, 0, 0, 0, 0});
        var clip = VoiceClip.read(webm);
        assertEquals("audio/webm", clip.mediaType());
        assertEquals("语音.webm", clip.fileName());

        var wav = new MockMultipartFile("file", "voice.wav", "audio/wav",
                "RIFF0000WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        assertEquals("audio/wav", VoiceClip.read(wav).mediaType());
    }

    @Test
    void rejectsEmptyOversizedAndUnknownAudio() {
        assertThrows(BizException.class, () -> VoiceClip.read(null));
        assertThrows(BizException.class, () -> VoiceClip.read(new MockMultipartFile("file", new byte[0])));
        assertThrows(BizException.class, () -> VoiceClip.read(new MockMultipartFile("file", "voice.webm",
                "audio/webm", new byte[20 * 1024 * 1024 + 1])));
        assertThrows(BizException.class, () -> VoiceClip.read(new MockMultipartFile("file", "voice.bin",
                "application/octet-stream", "not audio".getBytes(StandardCharsets.US_ASCII))));
    }
}
