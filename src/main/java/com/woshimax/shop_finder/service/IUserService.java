package com.woshimax.shop_finder.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.woshimax.shop_finder.dto.LoginFormDTO;
import com.woshimax.shop_finder.dto.Result;
import com.woshimax.shop_finder.entity.User;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author woshimax
 * @since 2024-3
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result queryUserById(Long id);

    Result logout(HttpServletRequest httpServletRequest);

    Result sign();

    Result signCount();
}
