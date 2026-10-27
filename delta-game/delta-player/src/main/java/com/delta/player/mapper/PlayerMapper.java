package com.delta.player.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.delta.player.entity.Player;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PlayerMapper extends BaseMapper<Player> {

    /** 含逻辑删除行。MyBatis-Plus 的 selectById 会滤掉 deleted=1。 */
    @Select("SELECT id FROM player WHERE id = #{id} LIMIT 1")
    Long selectIdIncludingDeleted(@Param("id") Long id);

    /** 已删除但仍占着原 openid 的打手。 */
    @Select("SELECT id FROM player WHERE openid = #{openid} AND deleted = 1 LIMIT 1")
    Long selectDeletedIdByOpenid(@Param("openid") String openid);

    /** 改写已删除打手的 openid，给重新入驻腾位置。 */
    @Update("UPDATE player SET openid = #{released} WHERE id = #{id} AND deleted = 1")
    int releaseDeletedOpenid(@Param("id") Long id, @Param("released") String released);
}
