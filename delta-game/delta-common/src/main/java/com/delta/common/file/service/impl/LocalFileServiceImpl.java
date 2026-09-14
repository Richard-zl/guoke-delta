package com.delta.common.file.service.impl;

import com.delta.common.file.service.FileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class LocalFileServiceImpl implements FileService {
    /** 足够覆盖 RIFF/ftyp 等需要看到第 12 字节的魔数。 */
    private static final int MAGIC_LENGTH = 12;

    @Value("${file.upload.path:/data/upload}")
    private String uploadPath;
    @Value("${file.upload.url-prefix:/upload/}")
    private String urlPrefix;

    @Override
    public String upload(MultipartFile file) throws Exception {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        Path dir = Paths.get(uploadPath, datePath);
        Files.createDirectories(dir);

        String fileName = UUID.randomUUID().toString().replace("-", "") + resolveExtension(file);

        Path target = dir.resolve(fileName);
        file.transferTo(target.toFile());
        return urlPrefix + datePath + "/" + fileName;
    }

    /**
     * 扩展名以文件真实内容为准，识别不出时才退回原始文件名。
     * 小程序上传的临时文件名并不可靠：开发者工具录音的实际内容是 WebM，
     * 文件名却是 .wav，存成 .wav 后 iOS 无法解码且极难排查。
     */
    private String resolveExtension(MultipartFile file) throws IOException {
        byte[] head = new byte[MAGIC_LENGTH];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, MAGIC_LENGTH);
        }
        String sniffed = sniffExtension(head, read);
        return sniffed != null ? sniffed : originalExtension(file.getOriginalFilename());
    }

    private static String sniffExtension(byte[] head, int length) {
        if (startsWith(head, length, 0, "RIFF")) {
            if (startsWith(head, length, 8, "WAVE")) {
                return ".wav";
            }
            if (startsWith(head, length, 8, "WEBP")) {
                return ".webp";
            }
        }
        if (length >= 4 && (head[0] & 0xFF) == 0x1A && (head[1] & 0xFF) == 0x45
                && (head[2] & 0xFF) == 0xDF && (head[3] & 0xFF) == 0xA3) {
            return ".webm";
        }
        if (startsWith(head, length, 4, "ftyp")) {
            // ftyp 品牌决定是音频容器还是视频容器
            return startsWith(head, length, 8, "M4A") ? ".m4a" : ".mp4";
        }
        if (startsWith(head, length, 0, "OggS")) {
            return ".ogg";
        }
        if (startsWith(head, length, 0, "ID3")) {
            return ".mp3";
        }
        if (length >= 2 && (head[0] & 0xFF) == 0xFF) {
            int second = head[1] & 0xFF;
            // ADTS(AAC) 与 MPEG 音频帧头都以 0xFF 开头，用第二字节区分
            if ((second & 0xF6) == 0xF0) {
                return ".aac";
            }
            if ((second & 0xE0) == 0xE0) {
                return ".mp3";
            }
        }
        if (length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8
                && (head[2] & 0xFF) == 0xFF) {
            return ".jpg";
        }
        if (length >= 4 && (head[0] & 0xFF) == 0x89 && startsWith(head, length, 1, "PNG")) {
            return ".png";
        }
        if (startsWith(head, length, 0, "GIF")) {
            return ".gif";
        }
        return null;
    }

    private static boolean startsWith(byte[] head, int length, int offset, String ascii) {
        byte[] expected = ascii.getBytes(StandardCharsets.US_ASCII);
        if (length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (head[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static String originalExtension(String originalName) {
        if (originalName == null || !originalName.contains(".")) {
            return "";
        }
        String ext = originalName.substring(originalName.lastIndexOf("."));
        // 只接受形如 .mp3 的纯字母数字后缀，避免路径穿越等异常文件名
        return ext.matches("\\.[A-Za-z0-9]{1,6}") ? ext.toLowerCase() : "";
    }
}
