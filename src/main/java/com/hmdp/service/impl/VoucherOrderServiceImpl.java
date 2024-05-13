package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.resource.StringResource;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.config.RedissonConfig;
import com.hmdp.controller.VoucherOrderController;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
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
    //private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<VoucherOrder>(1024*1024);
    private static final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(10,10,200,TimeUnit.MINUTES,new ArrayBlockingQueue<>(12));


    //开启一个线程，异步创建订单并持久化到数据库
    @PostConstruct//这个注解——加载完类信息后就执行该注解标识的方法
    private void init(){
        threadPoolExecutor.submit(new VoucherOrderHandler());
    }
    private class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {
            while(true){
                //不断获取阻塞队列中的信息
                try {
                    /*
                    //1、从阻塞队列中获取订单信息
                    //VoucherOrder voucherOrder = orderTasks.take();
                    */

                    //1、从消息队列中取数据并判断：失败则continue再次重试获取，成功则创建订单返回，并调用xack通知xpending
                    //说明MapRecord：id+map——String-消息id，Object：map的k和v，我们放进去到键值对——说白了就是对map对封装
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            //偏移量用lastConsumed代表最新的——也就是">"
                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed())
                    );
                    //判断
                    if(list == null || list.isEmpty()){
                        continue;
                    }
                    //一个消息是list，里面的多个信息用map存
                    //解析list，取出信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    //从封装对map取出键值对map，然后构建订单
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    //确认消息xack
                    stringRedisTemplate.opsForStream().acknowledge("stream.orders","g1",record.getId());

                    //2、创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    //异常情况，去pending-list里拿数据
                    log.error("订单处理异常");
                    handlePendingList();
                }
            }
        }
    }

    private void handlePendingList() {
        while(true){
            //不断获取阻塞队列中的信息
            try {
                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1),
                        //这里偏移量用“0”表示从从头读（有问题的头开始读）
                        StreamOffset.create("stream.orders",ReadOffset.from("0"))
                );
                //判断
                if(list == null || list.isEmpty()){
                    break;
                }
                //一个消息是list，里面的多个信息用map存
                //解析list，取出信息
                MapRecord<String, Object, Object> record = list.get(0);
                //从封装对map取出键值对map，然后构建订单
                Map<Object, Object> value = record.getValue();
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                //确认消息xack
                stringRedisTemplate.opsForStream().acknowledge("stream.orders","g1",record.getId());

                //2、创建订单
                handleVoucherOrder(voucherOrder);
            } catch (Exception e) {
                //异常情况，去pending-list里拿数据
                try {
                    Thread.sleep(20);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }

                log.error("订单pending-list异常");
            }
        }
    }

    //设置代理类对象为属性
    private IVoucherOrderService proxy;
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //使用redisson来搞分布式锁
        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock: VoucherOrder:" + userId);
        boolean isLock = lock.tryLock();//默认参数 失败立即返回（-1），超时30s自动释放
        if(!isLock){
            log.error("不允许重复下单");
            return;
        }

        try{
            //获取代理对象底层也是ThreadLocal，因此作为属性——主线程和子线程都访问的到

            proxy.createVoucherOrder(voucherOrder);
        }finally {
            lock.unlock();
        }
    }

    private final static DefaultRedisScript<Long> SECKILL_SCRIPT;
    static{
        SECKILL_SCRIPT = new DefaultRedisScript<>();

        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    //修改使用阻塞队列为redis的stream消费组消息队列
    @Override
    public Result seckillVoucher(Long voucherId) {
        //异步秒杀——优化1、将扣减库存和一人一单确认交给redis的lua脚本处理
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");//基本型通过String.valueOf()转String，包装类用toString()
        //注意lua脚本里的参数都是字符串形式，传进去需要toString
        //在lua脚本中完成资格判断+将基本信息放入消息队列
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(),String.valueOf(orderId));
        int res = result.intValue();
        if(res == 1){
            return Result.fail("库存不足");
        }else if(res == 2){
            return Result.fail("不可重复下单");
        }


        //主线程初始化 代理对象
        proxy = (IVoucherOrderService)AopContext.currentProxy();
        return Result.ok(orderId);

    }

    /*@Override
    public Result seckillVoucher(Long voucherId) {
        //异步秒杀——优化1、将扣减库存和一人一单确认交给redis的lua脚本处理
        Long userId = UserHolder.getUser().getId();
        //注意lua脚本里的参数都是字符串形式，传进去需要toString
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString());
        int res = result.intValue();
        if(res == 1){
            return Result.fail("库存不足");
        }else if(res == 2){
            return Result.fail("不可重复下单");
        }


        //异步秒杀——优化2、阻塞队列+异步下单（持久化）：将下单信息放入阻塞队列，
        long orderId = redisIdWorker.nextId("order");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        voucherOrder.setId(orderId);
        //准备好基础信息，暂时不下单，放入阻塞队列
        //主线程初始化 代理对象
        proxy = (IVoucherOrderService)AopContext.currentProxy();
        orderTasks.add(voucherOrder);
        return Result.ok(orderId);

    }*/

/*    @Override
    public Result seckillVoucher(Long voucherId) {


        //秒杀下单：判断秒杀时间和优惠券库存

        //查秒杀券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //判断秒杀开始和结束
        LocalDateTime beginTime = voucher.getBeginTime();
        LocalDateTime endTime = voucher.getEndTime();
        LocalDateTime now = LocalDateTime.now();
        if (beginTime.isAfter(now)) {
            return Result.fail("秒杀未开始");
        }
        if (endTime.isBefore(now)) {
            return Result.fail("秒杀已结束");
        }
        //判断库存是否足够
        if (voucher.getStock() <= 0) {
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


       *//*
       //用synchronize锁用户，实现一人一单——无法处理集群服务器情况
       //每个服务器都有一个锁监视器，因此需要使用分布式锁的技术——多个服务器一个锁监视器
        synchronized (userId.toString().intern()){
            //不能直接（this.)createVoucherOrder(voucherId,userId);调用方法，这样事务失效了
            //这种被@Transactional注解的方法都需要拿到代理类对象，然后调用方法
            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId,userId);
        }
        *//*

        //方案一：使用自己写的redis分布式锁实现一人一单
        //SimpleRedisLock lock = new SimpleRedisLock("VoucherOrder:" + userId, stringRedisTemplate);

        //方案二：使用redisson来搞分布式锁
        RLock lock = redissonClient.getLock("lock: VoucherOrder:" + userId);
        boolean isLock = lock.tryLock();//默认参数 失败立即返回（-1），超时30s自动释放
        if (!isLock) {
            return Result.fail("不允许重复下单");
        }

        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId, userId);
        } finally {
            lock.unlock();
        }


    }*/
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //需要判断一人一单  也就是对userId和voucherId的判断
        //这里是判断有无数据存在，并不会更新数据——不适合用乐观锁，该用悲观锁

        //synchronized锁用户
        //toString是会创建对象的，intern手动让程序去字符串常量池里找
            Integer count = query().eq("user_id", voucherOrder.getUserId()).eq("voucher_id", voucherOrder.getVoucherId()).count();
            if (count.equals(1)){
                log.error("用户购买过");
                return;
            }
            //方法2：利用mysql语句的原子性——缺点：要频繁访问数据库
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherOrder.getVoucherId())
                    .gt("stock", 0)  //利用mysql操作的原子性，在操作时判断库存
                    .update();
            if (!success) {
                log.error("库存不足");
                return;
            }

            //保存(创建） 订单
            save(voucherOrder);


    }
}
