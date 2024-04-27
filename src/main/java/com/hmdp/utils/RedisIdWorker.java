package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

//全局唯一id生成器
//订单号必须：1、打乱规律防止泄露信息，2、不会重复
//主要策略：1、uuid；2、redis自增；3、雪花算法（比较依赖于系统时间）；4、数据库自增——单独取一张表专门用来自增（redis自增的数据库版）
@Component
public class RedisIdWorker {


    static final Long BEGIN_TIMESTAMP = 1714176000L;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix){
        //一种位算法——1个符号位+31bit时间戳(秒数)转成的二进制+32bit由redis自增出来的值
        //1、生成时间戳（前半部分）
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSecond - BEGIN_TIMESTAMP;
        //2、生成序列号（后半部分）
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));//设定“：”方便按年/月/日统计
        //设置自增长
        long count = stringRedisTemplate.opsForValue().increment("inc" + keyPrefix +":" + date);
        long res = timeStamp << 32 | count;
        return res;
    }

//    public static void main(String[] args) {
//        System.out.println(LocalDateTime.of(2024,4,27,0,0,0).toEpochSecond(ZoneOffset.UTC));
//    }

}
