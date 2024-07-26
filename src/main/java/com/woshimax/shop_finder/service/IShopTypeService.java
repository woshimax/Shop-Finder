package com.woshimax.shop_finder.service;

import com.woshimax.shop_finder.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author woshimax
 * @since 2024-3
 */
public interface IShopTypeService extends IService<ShopType> {

    List<ShopType> queryShopTypeList();
}
