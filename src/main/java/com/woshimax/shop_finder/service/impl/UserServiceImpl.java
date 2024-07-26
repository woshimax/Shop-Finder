package com.woshimax.shop_finder.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.woshimax.shop_finder.dto.LoginFormDTO;
import com.woshimax.shop_finder.dto.Result;
import com.woshimax.shop_finder.dto.UserDTO;
import com.woshimax.shop_finder.entity.User;
import com.woshimax.shop_finder.mapper.UserMapper;
import com.woshimax.shop_finder.service.IUserService;
import com.woshimax.shop_finder.utils.RegexUtils;
import com.woshimax.shop_finder.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.woshimax.shop_finder.utils.RedisConstants.*;
import static com.woshimax.shop_finder.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author woshimax
 * @since 2024-3
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号合法
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号无效");
        }
        //生成验证码
        String code = RandomUtil.randomNumbers(6);
        /*保存验证码到session
        session.setAttribute("code",code);
        因为一个服务器有一个session，因此这种方式在集群服务器时还需要copy到其他服务器，效率很低——数据不共享
        redis满足数据共享，内存存储，同时也是key-value形式
        */
        //改成保存到redis——注意设置验证码有效期
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY +phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //发送验证码
        log.debug("发送验证码成功，验证码为：{}",code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号和验证码
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号无效");
        }
        //从session获取验证码改成——从redis获取验证码
        //Object code = session.getAttribute("code");

        String code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code1 = loginForm.getCode();
        if(code == null || !code.equals(code1)){
            return Result.fail("验证码错误");
        }
        //通过，查询用户是否存在
        User user = query().eq("phone", phone).one();
        if(user == null){
            //不存在，创建并保存用户到数据库
            user = createUserWithPhone(phone);
        }
        //保存用户到session
        //session.setAttribute("user", BeanUtil.copyProperties(user,UserDTO.class));

        //修改成保存用户到redis（这也是常用场景，前面先保存到mysql，这里再保存到redis）
        //保存用户到redis
        //1、随机生成token，作为登陆令牌
        String token = UUID.randomUUID().toString(true);
        //2、将User转成hashMap（因为redis中hash的每个key的value都是一个hashmap，这样可以直接丢map进去，不需要一个个存）
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName,fieldValue)->(fieldValue.toString())));

        //3、存入
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,userMap);
        //4、设置有效期——无操作的30分钟后失效
        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL,TimeUnit.MINUTES);
        return Result.ok(token);
    }

    @Override
    public Result queryUserById(Long id) {
        User user = getById(id);
        if(user != null){
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            return Result.ok(userDTO);
        }
        return Result.ok();
    }

    @Override
    public Result logout(HttpServletRequest httpServletRequest) {

        String token = httpServletRequest.getHeader("authorization");
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.delete(tokenKey);
        UserHolder.removeUser();
        return Result.ok();
    }

    @Override
    public Result signCount() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        int dayOfMonth = now.getDayOfMonth();
        //bitfield命令可以同时做get，set等多个命令（相当于命令集合体）
        //因此这个返回结果就可以说明了：一种命令对应一个结果，一堆命令集合返回的就是list
        //获取本月连续到今天到签到信息
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create().
                        get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
                        //这里dayOfMonth传入是指定总共几位，valueAt（）则是从第几位开始
        );

        if(result == null){
            return Result.ok(0);
        }

        Long num = result.get(0);
        if(num == null || num == 0){
            return Result.ok(0);
        }
        log.info("signNum:"+num);
        int count = 0;
        //遍历获取到的二进制串
        while(true){
            //和1做与运算（判断最后一位），运算完就右移
            if((num & 1) == 0){//
                break;
            }else{
                count++;
                num = num >>> 1;
            }
        }
        return Result.ok(count );
    }

    @Override
    public Result sign() {
        //获取当前用户
        Long userId = UserHolder.getUser().getId();
        //获取当天时间
        LocalDateTime now = LocalDateTime.now();
        //拼接key
        //格式化月+年
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        //获取当前是第几天——用于bitmap操作(bitmap操作的偏移量offset）
        int dayOfMonth = now.getDayOfMonth();
        //bitmap底层是string
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth-1,true);
        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        //创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10) );
        //保存用户
        save(user);
        return user;
    }
}
