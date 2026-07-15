package com.delta.common.file.controller;

import com.delta.common.domain.R;
import com.delta.common.file.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/common/file")
@RequiredArgsConstructor
public class FileController {
    private final FileService fileService;

    @PostMapping("/upload")
    public R<String> upload(@RequestParam("file") MultipartFile file) throws Exception {
        String url = fileService.upload(file);
        return R.ok(url);
    }
}
