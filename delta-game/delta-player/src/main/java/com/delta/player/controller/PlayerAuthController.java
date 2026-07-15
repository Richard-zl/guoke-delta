package com.delta.player.controller;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.delta.common.domain.R;
import com.delta.common.enums.UserTypeEnum;
import com.delta.common.mapper.CrossModuleMapper;
import com.delta.common.security.service.TokenService;
import com.delta.player.entity.Player;
import com.delta.player.service.PlayerService;
import com.delta.player.service.PlayerWalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/player/auth")
@RequiredArgsConstructor
public class PlayerAuthController {
    private final ObjectProvider<WxMaService> wxMaServiceProvider;
    private final PlayerService playerService;
    private final PlayerWalletService playerWalletService;
    private final TokenService tokenService;
    private final CrossModuleMapper crossModuleMapper;

    @PostMapping("/login")
    public R<com.delta.player.dto.PlayerLoginResponse> wxLogin(@RequestBody com.delta.player.dto.PlayerLoginRequest request) {
        WxMaService wxMaService = wxMaServiceProvider.getIfAvailable();
        if (wxMaService == null) {
            return R.fail("微信小程序登录未启用");
        }
        try {
            WxMaJscode2SessionResult session = wxMaService.getUserService().getSessionInfo(request.getCode());
            String openid = session.getOpenid();
            Player player = playerService.getByOpenid(openid);
            // 打手不存在，返回1002引导前端跳转入驻页面
            if (player == null) {
                return R.fail(1002, "尚未注册为打手，请先申请入驻");
            }
            // 检查打手各种状态
            switch (player.getStatus()) {
                case "PENDING":
                    return R.fail(1003, "入驻申请审核中，请耐心等待");
                case "REJECTED":
                    return R.fail(1004, "入驻申请已驳回，原因：" + player.getRejectReason());
                case "FROZEN":
                    return R.fail(1005, "账号已被冻结");
                case "ACTIVE":
                    break;
                default:
                    return R.fail("账号状态异常");
            }
            // 打手头像为空时，尝试使用同 openid 用户上传的头像并回写
            if (player.getAvatar() == null || player.getAvatar().isEmpty()) {
                String userAvatar = crossModuleMapper.selectUserAvatarByOpenid(openid);
                if (userAvatar != null && !userAvatar.isEmpty()) {
                    player.setAvatar(userAvatar);
                    playerService.updateById(player);
                }
            }
            String token = tokenService.createToken(player.getId(), UserTypeEnum.PLAYER);
            com.delta.player.dto.PlayerLoginResponse resp = new com.delta.player.dto.PlayerLoginResponse();
            resp.setToken(token);
            resp.setPlayerId(player.getId());
            resp.setNickname(player.getNickname());
            resp.setAvatar(player.getAvatar());
            return R.ok(resp);
        } catch (Exception e) {
            log.error("打手微信登录失败", e);
            return R.fail("登录失败: " + e.getMessage());
        }
    }

    /**
     * 打手入驻申请（首次注册）
     */
    @PostMapping("/register")
    public R<Void> register(@RequestBody com.delta.player.dto.PlayerLoginRequest request) {
        WxMaService wxMaService = wxMaServiceProvider.getIfAvailable();
        if (wxMaService == null) {
            return R.fail("微信小程序登录未启用");
        }
        try {
            WxMaJscode2SessionResult session = wxMaService.getUserService().getSessionInfo(request.getCode());
            String openid = session.getOpenid();
            Player existing = playerService.getByOpenid(openid);
            if (existing != null) {
                return R.fail("已存在申请记录，当前状态：" + existing.getStatus());
            }
            Player player = new Player();
            player.setOpenid(openid);
            player.setNickname("");
            player.setAvatar("");
            player.setStatus("PENDING");
            // 若该 openid 已存在用户且用户上传过头像，则打手使用用户头像
            String userAvatar = crossModuleMapper.selectUserAvatarByOpenid(openid);
            if (userAvatar != null && !userAvatar.isEmpty()) {
                player.setAvatar(userAvatar);
            }
            playerService.save(player);
            playerWalletService.initWallet(player.getId());
            return R.ok();
        } catch (Exception e) {
            log.error("打手入驻申请失败", e);
            return R.fail("申请失败: " + e.getMessage());
        }
    }
}
