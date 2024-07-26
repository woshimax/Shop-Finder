package com.woshimax.shop_finder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.woshimax.shop_finder.entity.Voucher;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author woshimax
 * @since 2024-3
 */
public interface VoucherMapper extends BaseMapper<Voucher> {

    List<Voucher> queryVoucherOfShop(@Param("shopId") Long shopId);
}
