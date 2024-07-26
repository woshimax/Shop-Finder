package com.woshimax.shop_finder.controller;


import com.woshimax.shop_finder.dto.Result;
import com.woshimax.shop_finder.entity.ShopType;
import com.woshimax.shop_finder.service.IShopTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author woshimax
 * @since 2024-3
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    @GetMapping("list")
    public Result queryTypeList() {
        List<ShopType> typeList = typeService
                .queryShopTypeList();
        if(typeList == null) return Result.fail("无商品种类信息");
        return Result.ok(typeList);
    }
}
