package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author lfy
 * @since 2025-3-18
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //查询
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())){
            return  Result.fail("秒杀尚未开始");
        }
        //秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())){
            return  Result.fail("秒杀已经结束");
        }
        //库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }

        Long userId = UserHolder.getUser().getId();

        //        synchronized (userId.toString().intern()) {
        //创建锁对象
//        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock(); //无参不重试
        if (!isLock){
            //获取锁失败 返回错误或重试
            return Result.fail("不允许重复下单！");
        }
        try {
            //获取代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }finally {
            lock.unlock();
        }


    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //一人一单
        Long userId = UserHolder.getUser().getId();
        //根据Id加锁
            Long count = lambdaQuery().eq(VoucherOrder::getUserId, userId).eq(VoucherOrder::getVoucherId, voucherId).count();
            if (count > 0) {
                return Result.fail("用户已购买过一次！");
            }
            //减扣库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1") //set stock = stock - 1
                    .eq("voucher_id", voucherId) //where
                    .gt("stock", 0) //乐观锁
                    .update();
            if (!success){
                return Result.fail("扣减失败！");
            }
            //创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            voucherOrder.setUserId(UserHolder.getUser().getId());
    //        voucherOrder.setUserId(123L);
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);

            return Result.ok(orderId);
    }


}
