package com.woshimax.shop_finder.service;

import com.woshimax.shop_finder.dto.Result;
import com.woshimax.shop_finder.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author woshimax
 * @since 2024-3
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    void createVoucherOrder(VoucherOrder voucherOrder);
}
