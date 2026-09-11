package com.delta.player.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.common.security.utils.SecurityUtils;
import com.delta.player.dto.PlayerShowcaseCardVO;
import com.delta.player.dto.PlayerShowcaseDetailVO;
import com.delta.player.dto.PlayerShowcaseSelfVO;
import com.delta.player.service.PlayerShowcaseService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/player/showcase")
@RequiredArgsConstructor
public class PlayerShowcaseController {
    private final PlayerShowcaseService playerShowcaseService;

    @GetMapping("/active")
    public R<Page<PlayerShowcaseCardVO>> active(PageQuery query) {
        int pageNum = query.getPageNum() == null ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 10 : query.getPageSize();
        return R.ok(playerShowcaseService.pageActive(pageNum, pageSize));
    }

    @GetMapping("/detail/{playerId}")
    public R<PlayerShowcaseDetailVO> detail(@PathVariable Long playerId) {
        return R.ok(playerShowcaseService.getPublicDetail(playerId));
    }

    @GetMapping("/me")
    public R<PlayerShowcaseSelfVO> me() {
        return R.ok(playerShowcaseService.getSelf(SecurityUtils.getUserId()));
    }

    @PutMapping("/me/voice")
    public R<Void> saveVoice(@RequestBody VoiceBody body) {
        playerShowcaseService.saveVoice(SecurityUtils.getUserId(), body.getUrl(), body.getSeconds());
        return R.ok();
    }

    @PutMapping("/me/gallery")
    public R<Void> saveGallery(@RequestBody GalleryBody body) {
        playerShowcaseService.saveGallery(SecurityUtils.getUserId(), body.getUrls());
        return R.ok();
    }

    @Data
    public static class VoiceBody {
        private String url;
        private Integer seconds;
    }

    @Data
    public static class GalleryBody {
        private List<String> urls;
    }
}
