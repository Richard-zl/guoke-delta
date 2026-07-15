package com.delta.user.controller;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.delta.common.domain.R;
import com.delta.common.enums.UserTypeEnum;
import com.delta.common.mapper.CrossModuleMapper;
import com.delta.common.security.service.TokenService;
import com.delta.common.security.utils.SecurityUtils;
import com.delta.user.dto.H5LoginRequest;
import com.delta.user.dto.LoginResponse;
import com.delta.user.dto.WxLoginRequest;
import com.delta.user.entity.User;
import com.delta.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/user/auth")
@RequiredArgsConstructor
public class UserAuthController {
    private final ObjectProvider<WxMaService> wxMaServiceProvider;
    private final UserService userService;
    private final TokenService tokenService;
    private final CrossModuleMapper crossModuleMapper;

    @Value("${auth.h5.login-code:}")
    private String h5LoginCode;

    @PostMapping("/login")
    public R<LoginResponse> wxLogin(@RequestBody WxLoginRequest request) {
        WxMaService wxMaService = wxMaServiceProvider.getIfAvailable();
        if (wxMaService == null) {
            return R.fail("微信小程序登录未启用，请使用H5手机号登录");
        }
        try {
            WxMaJscode2SessionResult session = wxMaService.getUserService().getSessionInfo(request.getCode());
            String openid = session.getOpenid();
            User user = userService.getByOpenid(openid);
            if (user == null) {
                if (request.getPhoneCode() == null || request.getPhoneCode().isEmpty()) {
                    return R.fail(1006, "首次登录请先授权手机号");
                }

                String phone;
                try {
                    var phoneInfo = wxMaService.getUserService().getPhoneNoInfo(request.getPhoneCode());
                    phone = phoneInfo.getPhoneNumber();
                } catch (Exception ex) {
                    log.warn("首次登录获取手机号失败", ex);
                    return R.fail("获取手机号失败，请重试");
                }

                if (phone == null || phone.isEmpty()) {
                    return R.fail("获取手机号失败，请重试");
                }

                User phoneUser = userService.getByPhone(phone);
                if (phoneUser != null) {
                    String existingOpenid = phoneUser.getOpenid();
                    if (existingOpenid != null && !existingOpenid.isBlank() && !existingOpenid.startsWith("h5:")) {
                        return R.fail("该手机号已绑定其他微信账号");
                    }
                    phoneUser.setOpenid(openid);
                    userService.updateById(phoneUser);
                    user = phoneUser;
                } else {
                    String nickname = request.getNickname() != null ? request.getNickname().trim() : "";
                    String avatar = request.getAvatar() != null ? request.getAvatar().trim() : "";
                    user = userService.loginOrRegister(openid, nickname, avatar);
                    user.setPhone(phone);
                    userService.updateById(user);
                }
            }
            if (user.getStatus() == 0) return R.fail("账号已被封禁");
            return R.ok(buildLoginResponse(user));
        } catch (Exception e) {
            log.error("微信登录失败", e);
            return R.fail("登录失败: " + e.getMessage());
        }
    }

    @PostMapping("/h5-login")
    public R<LoginResponse> h5Login(@RequestBody H5LoginRequest request) {
        String phone = request.getPhone() == null ? "" : request.getPhone().trim();
        String code = request.getCode() == null ? "" : request.getCode().trim();
        if (!phone.matches("^1\\d{10}$")) {
            return R.fail("请输入正确的手机号");
        }
        if (h5LoginCode == null || h5LoginCode.isBlank()) {
            return R.fail("H5登录验证码未配置");
        }
        if (!h5LoginCode.equals(code)) {
            return R.fail("验证码错误");
        }
        User user = userService.loginOrRegisterByPhone(phone);
        if (user.getStatus() == 0) return R.fail("账号已被封禁");
        return R.ok(buildLoginResponse(user));
    }

    private LoginResponse buildLoginResponse(User user) {
        String token = tokenService.createToken(user.getId(), UserTypeEnum.USER);
        LoginResponse resp = new LoginResponse();
        resp.setToken(token);
        resp.setUserId(user.getId());
        resp.setNickname(user.getNickname());
        resp.setAvatar(user.getAvatar());
        return resp;
    }

    /**
     * 用户切换到打手端：校验当前用户是否为打手，若是则颁发打手专用 token
     * 需携带用户 token 调用，返回打手 token 供前端保存，打手端请求使用此 token
     */
    @PostMapping("/switch-to-player")
    public R<LoginResponse> switchToPlayer() {
        Long userId = SecurityUtils.getUserId();
        Long playerId = crossModuleMapper.selectPlayerIdByUserId(userId);
        if (playerId == null) {
            return R.fail(1002, "尚未注册为打手，请先申请入驻");
        }
        String status = crossModuleMapper.selectPlayerStatus(playerId);
        if ("PENDING".equals(status)) {
            return R.fail(1003, "入驻申请审核中，请耐心等待");
        }
        if ("REJECTED".equals(status)) {
            return R.fail(1004, "入驻申请已驳回，请重新申请");
        }
        if ("FROZEN".equals(status)) {
            return R.fail(1005, "账号已被冻结");
        }
        if (!"ACTIVE".equals(status)) {
            return R.fail("打手状态异常");
        }
        String token = tokenService.createToken(playerId, UserTypeEnum.PLAYER);
        LoginResponse resp = new LoginResponse();
        resp.setToken(token);
        resp.setUserId(playerId);
        return R.ok(resp);
    }
}
