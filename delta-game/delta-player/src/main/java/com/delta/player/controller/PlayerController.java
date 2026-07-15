package com.delta.player.controller;

import com.delta.common.domain.R;
import com.delta.common.security.utils.SecurityUtils;
import com.delta.common.utils.ImageListUtils;
import com.delta.player.dto.PlayerProfileVO;
import com.delta.player.entity.Player;
import com.delta.player.entity.PlayerWallet;
import com.delta.player.service.PlayerService;
import com.delta.player.service.PlayerWalletService;
import com.delta.pay.service.PaymentService;
import com.delta.user.entity.User;
import com.delta.user.service.UserService;
import com.delta.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/player")
@RequiredArgsConstructor
public class PlayerController {
    private final PlayerService playerService;
    private final PlayerWalletService playerWalletService;
    private final UserService userService;
    private final PaymentService paymentService;
    private final SysConfigService sysConfigService;

    @GetMapping("/profile")
    public R<PlayerProfileVO> profile() {
        Long playerId = SecurityUtils.getUserId();
        Player player = playerService.getById(playerId);
        if (player == null) return R.fail(1002, "打手记录不存在");
        PlayerWallet wallet = playerWalletService.getByPlayerId(playerId);
        PlayerProfileVO vo = new PlayerProfileVO();
        vo.setId(player.getId());
        vo.setNickname(player.getNickname());
        vo.setAvatar(player.getAvatar());
        vo.setRealName(player.getRealName());
        vo.setPhone(player.getPhone());
        vo.setStatus(player.getStatus());
        vo.setGameLevel(player.getGameLevel());
        vo.setAvgRating(player.getAvgRating());
        vo.setOrderCount(player.getOrderCount());
        vo.setCompleteRate(player.getCompleteRate());
        vo.setIsOnline(player.getIsOnline());
        if (wallet != null) {
            vo.setBalance(wallet.getBalance());
            vo.setTotalIncome(wallet.getTotalIncome());
        }
        return R.ok(vo);
    }

    @PutMapping("/profile")
    public R<Void> updateProfile(@RequestBody Player player) {
        player.setId(SecurityUtils.getUserId());
        player.setOpenid(null);
        player.setStatus(null);
        player.setNickname(null);  // 昵称只能由客服修改
        player.setProofImages(ImageListUtils.normalize(player.getProofImages()));
        playerService.updateById(player);
        return R.ok();
    }

    @PostMapping("/apply")
    public R<Void> apply(@RequestBody Player player) {
        Long userId = SecurityUtils.getUserId();
        boolean depositRequired = "true".equals(sysConfigService.getConfigValue("player.deposit_required", "true"));
        String depositPaymentNo = player.getDepositPaymentNo();
        if (depositRequired) {
            if (depositPaymentNo == null || depositPaymentNo.isBlank()) {
                return R.fail("请先支付押金");
            }
            if (!paymentService.verifyPlayerDepositPayment(depositPaymentNo, userId)) {
                return R.fail("押金支付无效或未到账，请完成支付后再提交");
            }
        }
        Player existing = playerService.getById(userId);
        player.setProofImages(ImageListUtils.normalize(player.getProofImages()));
        if (existing == null) {
            // 从用户表获取基本信息
            User user = userService.getById(userId);
            if (user == null) return R.fail("用户不存在");
            // 新用户申请，插入新记录
            player.setId(userId);
            player.setOpenid(user.getOpenid());
            if (player.getNickname() == null) player.setNickname(user.getNickname());
            if (player.getAvatar() == null) player.setAvatar(user.getAvatar());
            player.setStatus("PENDING");
            player.setOrderCount(0);
            player.setAvgRating(BigDecimal.ZERO);
            player.setCompleteRate(BigDecimal.ZERO);
            player.setDepositPaymentNo(depositPaymentNo);
            playerService.save(player);
        } else {
            // 已有记录，更新资料重新提交
            player.setId(userId);
            player.setOpenid(null); // 不允许修改openid
            player.setStatus("PENDING");
            player.setRejectReason(null);
            player.setDepositPaymentNo(depositPaymentNo);
            playerService.updateById(player);
        }
        return R.ok();
    }

    @PostMapping("/online-status")
    public R<Void> toggleOnlineStatus(@RequestParam("online") Boolean online) {
        Long playerId = SecurityUtils.getUserId();
        Player p = new Player();
        p.setId(playerId);
        p.setIsOnline(Boolean.TRUE.equals(online) ? 1 : 0);
        p.setLastOnlineAt(LocalDateTime.now());
        playerService.updateById(p);
        return R.ok();
    }
}
