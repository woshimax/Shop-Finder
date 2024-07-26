package com.woshimax.shop_finder.service;

import com.woshimax.shop_finder.dto.Result;
import com.woshimax.shop_finder.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author woshimax
 * @since 2024-3
 */
public interface IShopService extends IService<Shop> {
    Result queryById(Long id);
    Result update(Shop shop);

    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
