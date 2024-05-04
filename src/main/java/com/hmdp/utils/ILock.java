package com.hmdp.utils;

public interface ILock {
    //尝试获取锁
    boolean tryLock(long timeoutSec);//设定超时自动释放锁时间
    //释放锁
    void unLock();
}
