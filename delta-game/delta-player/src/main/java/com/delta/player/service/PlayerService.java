package com.delta.player.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.delta.player.entity.Player;

public interface PlayerService extends IService<Player> {
    Player getByOpenid(String openid);
    Player loginOrRegister(String openid, String nickname, String avatar);

    /** 主键是否已有打手行，包含已逻辑删除的记录。 */
    boolean idTakenIncludingDeleted(Long id);

    /** 已删除打手若仍占用该 openid，改写掉，避免重新入驻撞唯一索引。 */
    void releaseDeletedOpenid(String openid);
}
