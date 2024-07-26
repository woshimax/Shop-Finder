package com.woshimax.shop_finder.service;

import com.woshimax.shop_finder.dto.Result;
import com.woshimax.shop_finder.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author woshimax
 * @since 2024-3
 */
public interface IFollowService extends IService<Follow> {

    Result follow(Long followUserId,Boolean isFollow);

    Result isFollow(Long followUserId);

    Result followCommon(Long id);


}
