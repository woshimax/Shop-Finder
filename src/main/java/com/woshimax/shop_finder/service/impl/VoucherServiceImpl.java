package com.woshimax.shop_finder.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.woshimax.shop_finder.dto.Result;
import com.woshimax.shop_finder.entity.Voucher;
import com.woshimax.shop_finder.mapper.VoucherMapper;
import com.woshimax.shop_finder.entity.SeckillVoucher;
import com.woshimax.shop_finder.service.ISeckillVoucherService;
import com.woshimax.shop_finder.service.IVoucherService;
import com.woshimax.shop_finder.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author woshimax
 * @since 2024-3
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;



    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);
        //异步秒杀优化step1:
        //使用redis来完成库存判断——减轻数据库压力
        //在添加到优惠券信息到数据库同时，保存库存到redis
        stringRedisTemplate.opsForValue().set(RedisConstants.SECKILL_STOCK_KEY,voucher.getStock().toString());
    }
}
