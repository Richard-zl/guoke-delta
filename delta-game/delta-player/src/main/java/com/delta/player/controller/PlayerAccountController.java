package com.delta.player.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.common.domain.R;
import com.delta.common.security.utils.SecurityUtils;
import com.delta.player.entity.PlayerAccount;
import com.delta.player.service.PlayerAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/player/account")
@RequiredArgsConstructor
public class PlayerAccountController {
    private final PlayerAccountService playerAccountService;

    @GetMapping({"" , "/list"})
    public R<List<PlayerAccount>> list() {
        return R.ok(playerAccountService.list(
                new LambdaQueryWrapper<PlayerAccount>().eq(PlayerAccount::getPlayerId, SecurityUtils.getUserId())));
    }

    @PostMapping
    public R<Void> add(@RequestBody PlayerAccount account) {
        account.setPlayerId(SecurityUtils.getUserId());
        playerAccountService.save(account);
        return R.ok();
    }

    @PutMapping
    public R<Void> update(@RequestBody PlayerAccount account) {
        account.setPlayerId(SecurityUtils.getUserId());
        playerAccountService.updateById(account);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        playerAccountService.removeById(id);
        return R.ok();
    }
}
