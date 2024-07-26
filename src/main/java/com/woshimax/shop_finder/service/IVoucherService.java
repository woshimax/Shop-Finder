package com.woshimax.shop_finder.service;

import com.woshimax.shop_finder.dto.Result;
import com.woshimax.shop_finder.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author woshimax
 * @since 2024-3
 */
public interface IVoucherService extends IService<Voucher> {

    Result queryVoucherOfShop(Long shopId);

    void addSeckillVoucher(Voucher voucher);
}
