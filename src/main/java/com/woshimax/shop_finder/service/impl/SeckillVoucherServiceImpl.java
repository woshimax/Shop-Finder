package com.woshimax.shop_finder.service.impl;

import com.woshimax.shop_finder.entity.SeckillVoucher;
import com.woshimax.shop_finder.mapper.SeckillVoucherMapper;
import com.woshimax.shop_finder.service.ISeckillVoucherService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 服务实现类
 * </p>
 *
 * @author woshimax
 * @since 2024-3
 */
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {

}
