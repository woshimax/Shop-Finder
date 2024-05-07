package com.hmdp.service.impl;

import cn.hutool.core.io.resource.StringResource;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.config.RedissonConfig;
import com.hmdp.controller.VoucherOrderController;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedissonClient redissonClient;
    @Override
    public Result seckillVoucher(Long voucherId) {
        //秒杀下单：判断秒杀时间和优惠券库存

        //查秒杀券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //判断秒杀开始和结束
        LocalDateTime beginTime = voucher.getBeginTime();
        LocalDateTime endTime = voucher.getEndTime();
        LocalDateTime now = LocalDateTime.now();
        if(beginTime.isAfter(now)){
            return Result.fail("秒杀未开始");
        }
        if(endTime.isBefore(now)){
            return Result.fail("秒杀已结束");
        }
        //判断库存是否足够
        if(voucher.getStock() <= 0){
            return Result.fail("秒杀券已抢完");
        }
        //扣除库存
        //注意：乐观锁对应用场景——用于更新数据
        //超卖问题解决方案1:乐观锁——缺点：单纯加乐观锁可能会导致失败率较高（比如一大堆线程操作同一个版本号），因此还需要加入重试操作
//        boolean success = seckillVoucherService.update()
//                .setSql("stock = stock - 1")
//                .eq("voucher_id", voucherId)
//                .eq("stock",voucher.getStock())  //乐观锁step1:判断版本号
//                .update();
        Long userId = UserHolder.getUser().getId();


       /*
       //用synchronize锁用户，实现一人一单——无法处理集群服务器情况
       //每个服务器都有一个锁监视器，因此需要使用分布式锁的技术——多个服务器一个锁监视器
        synchronized (userId.toString().intern()){
            //不能直接（this.)createVoucherOrder(voucherId,userId);调用方法，这样事务失效了
            //这种被@Transactional注解的方法都需要拿到代理类对象，然后调用方法
            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId,userId);
        }
        */

        //方案一：使用自己写的redis分布式锁实现一人一单
        //SimpleRedisLock lock = new SimpleRedisLock("VoucherOrder:" + userId, stringRedisTemplate);

        //方案二：使用redisson来搞分布式锁
        RLock lock = redissonClient.getLock("lock: VoucherOrder:" + userId);
        boolean isLock = lock.tryLock();//默认参数 失败立即返回（-1），超时30s自动释放
        if(!isLock){
            return Result.fail("不允许重复下单");
        }

        try{
            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId,userId);
        }finally {
            lock.unlock();
        }


    }

    @Transactional
    public Result createVoucherOrder(Long voucherId,Long userId) {
        //需要判断一人一单  也就是对userId和voucherId的判断
        //这里是判断有无数据存在，并不会更新数据——不适合用乐观锁，该用悲观锁

        //synchronized锁用户
        //toString是会创建对象的，intern手动让程序去字符串常量池里找
            Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            if (count.equals(1)) return Result.fail("该用户已购买");
            //方法2：利用mysql语句的原子性——缺点：要频繁访问数据库
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0)  //利用mysql操作的原子性，在操作时判断库存
                    .update();
            if (!success) {
                return Result.fail("秒杀券已抢完");
            }

            //创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            //传入订单id，代金券id，用户id
            long orderId = redisIdWorker.nextId("order");

            voucherOrder.setVoucherId(voucherId);
            voucherOrder.setUserId(userId);
            voucherOrder.setId(orderId);
            save(voucherOrder);

            return Result.ok(orderId);

    }
}
