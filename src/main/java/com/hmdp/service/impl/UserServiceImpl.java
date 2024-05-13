package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private StringRedisTemplate redisTemplate;
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
        redisTemplate.opsForValue().set(LOGIN_CODE_KEY +phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

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

        String code = redisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
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
        redisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,userMap);
        //4、设置有效期——无操作的30分钟后失效
        redisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL,TimeUnit.MINUTES);
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
