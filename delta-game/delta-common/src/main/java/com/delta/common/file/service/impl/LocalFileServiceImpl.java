package com.delta.common.file.service.impl;

import com.delta.common.file.service.FileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class LocalFileServiceImpl implements FileService {
    @Value("${file.upload.path:/data/upload}")
    private String uploadPath;
    @Value("${file.upload.url-prefix:/upload/}")
    private String urlPrefix;

    @Override
    public String upload(MultipartFile file) throws Exception {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        Path dir = Paths.get(uploadPath, datePath);
        Files.createDirectories(dir);

        String originalName = file.getOriginalFilename();
        String ext = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf(".")) : "";
        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;

        Path target = dir.resolve(fileName);
        file.transferTo(target.toFile());
        return urlPrefix + datePath + "/" + fileName;
    }
}
