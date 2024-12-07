package com.jiawa.train.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.EnumUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jiawa.train.business.domain.*;
import com.jiawa.train.business.enums.ConfirmOrderStatusEnum;
import com.jiawa.train.business.enums.SeatColEnum;
import com.jiawa.train.business.enums.SeatTypeEnum;
import com.jiawa.train.business.mapper.ConfirmOrderMapper;
import com.jiawa.train.business.req.ConfirmOrderDoReq;
import com.jiawa.train.business.req.ConfirmOrderQueryReq;
import com.jiawa.train.business.req.ConfirmOrderTicketReq;
import com.jiawa.train.business.resp.ConfirmOrderQueryResp;
import com.jiawa.train.common.context.LoginMemberContext;
import com.jiawa.train.common.exception.BusinessException;
import com.jiawa.train.common.exception.BusinessExceptionEnum;
import com.jiawa.train.common.resp.PageResp;
import com.jiawa.train.common.util.SnowUtil;
import jakarta.annotation.Resource;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ConfirmOrderService {

    private static final Logger LOG = LoggerFactory.getLogger(ConfirmOrderService.class);

    @Resource
    private ConfirmOrderMapper confirmOrderMapper;

    @Resource
    private DailyTrainTicketService dailyTrainTicketService;

    @Resource
    private DailyTrainCarriageService dailyTrainCarriageService;

    @Resource
    private DailyTrainSeatService dailyTrainSeatService;

    @Resource AfterConfirmOrderService afterConfirmOrderService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Resource
    private SkTokenService skTokenService;

    public void save(ConfirmOrderDoReq req) {
        DateTime now = DateTime.now();
        ConfirmOrder confirmOrder = BeanUtil.copyProperties(req, ConfirmOrder.class);
        if (ObjectUtil.isNull(confirmOrder.getId())) {
            confirmOrder.setId(SnowUtil.getSnowflakeNextId());
            confirmOrder.setCreateTime(now);
            confirmOrder.setUpdateTime(now);
            confirmOrderMapper.insert(confirmOrder);
        } else {
            confirmOrder.setUpdateTime(now);
            confirmOrderMapper.updateByPrimaryKey(confirmOrder);
        }
    }

    public PageResp<ConfirmOrderQueryResp> queryList(ConfirmOrderQueryReq req) {
        ConfirmOrderExample confirmOrderExample = new ConfirmOrderExample();
        confirmOrderExample.setOrderByClause("id desc");
        ConfirmOrderExample.Criteria criteria = confirmOrderExample.createCriteria();

        LOG.info("查询页码：{}", req.getPage());
        LOG.info("每页条数：{}", req.getSize());
        PageHelper.startPage(req.getPage(), req.getSize());
        List<ConfirmOrder> confirmOrderList = confirmOrderMapper.selectByExample(confirmOrderExample);

        PageInfo<ConfirmOrder> pageInfo = new PageInfo<>(confirmOrderList);
        LOG.info("总行数：{}", pageInfo.getTotal());
        LOG.info("总页数：{}", pageInfo.getPages());

        List<ConfirmOrderQueryResp> list = BeanUtil.copyToList(confirmOrderList, ConfirmOrderQueryResp.class);

        PageResp<ConfirmOrderQueryResp> pageResp = new PageResp<>();
        pageResp.setTotal(pageInfo.getTotal());
        pageResp.setList(list);
        return pageResp;
    }

    public void delete(Long id) {
        confirmOrderMapper.deleteByPrimaryKey(id);
    }

    public void doConfirm(ConfirmOrderDoReq req) {
        // 校验令牌余量
        boolean validSkToken = skTokenService.validSkToken(req.getDate(), req.getTrainCode(), LoginMemberContext.getId());
        if (validSkToken) {
            LOG.info("令牌校验通过");
        } else {
            LOG.info("令牌校验不通过");
            throw new BusinessException(BusinessExceptionEnum.CONFIRM_ORDER_SK_TOKEN_FAIL);
        }

        String lockKey = DateUtil.formatDate(req.getDate()) + "-" + req.getTrainCode();
        RLock lock = null;
        try {
            // 使用redisson，自带看门狗
            lock = redissonClient.getLock(lockKey);
            /**
             waitTime – the maximum time to acquire the lock 等待获取锁时间(最大尝试获得锁的时间)，超时返回false
             leaseTime – lease time 锁时长，即n秒后自动释放锁
             time unit – time unit 时间单位
             */
            // boolean tryLock = lock.tryLock(30, 10, TimeUnit.SECONDS); // 不带看门狗
            boolean tryLock = lock.tryLock(0, TimeUnit.SECONDS); // 带看门狗
            if (tryLock) {
                LOG.info("恭喜，抢到锁了！");
                // 可以把下面这段放开，只用一个线程来测试，看看redisson的看门狗效果
                // for (int i = 0; i < 30; i++) {
                //     Long expire = redisTemplate.opsForValue().getOperations().getExpire(lockKey);
                //     LOG.info("锁过期时间还有：{}", expire);
                //     Thread.sleep(1000);
                // }
            } else {
                // 只是没抢到锁，并不知道票抢完了没，所以提示稍候再试
                LOG.info("很遗憾，没抢到锁");
                throw new BusinessException(BusinessExceptionEnum.CONFIRM_ORDER_LOCK_FAIL);
            }

            //省略业务数据校验，如：车次是否存在，余票是否存在，车次是否在有效期内，ticket条数>0，同乘客同车次是否已经买过

            // 保存确认订单表，状态初始
            DateTime now = DateTime.now();
            Date date = req.getDate();
            String trainCode = req.getTrainCode();
            String start = req.getStart();
            String end = req.getEnd();
            List<ConfirmOrderTicketReq> tickets = req.getTickets();
            ConfirmOrder confirmOrder = new ConfirmOrder();
            confirmOrder.setId(SnowUtil.getSnowflakeNextId());
            confirmOrder.setCreateTime(now);
            confirmOrder.setUpdateTime(now);
            confirmOrder.setMemberId(LoginMemberContext.getId());
            confirmOrder.setDate(date);
            confirmOrder.setTrainCode(trainCode);
            confirmOrder.setStart(start);
            confirmOrder.setEnd(end);
            confirmOrder.setDailyTrainTicketId(req.getDailyTrainTicketId());
            confirmOrder.setStatus(ConfirmOrderStatusEnum.INIT.getCode());
            confirmOrder.setTickets(JSON.toJSONString(tickets));
            confirmOrderMapper.insert(confirmOrder);

            // 查出余票记录，需要得到真实的库存
            DailyTrainTicket dailyTrainTicket = dailyTrainTicketService.selectByUnique(date, trainCode, start, end);
            LOG.info("查出余票记录：{}", dailyTrainTicket);

            //扣减余票数量，并判断余票是否足够
            reduceTickets(req, dailyTrainTicket);

            // 最终的选座结果
            List<DailyTrainSeat> finalSeatList = new ArrayList<>();
            // 计算相对第一个座位的偏移值
            // 比如选择的是C1,D2，则偏移值是：[0,5]
            // 比如选择的是A1,B1,C1，则偏移值是：[0,1,2]
            ConfirmOrderTicketReq ticketReq0 = tickets.get(0);
            if(StrUtil.isNotBlank(ticketReq0.getSeat())) {
                LOG.info("本次购票有选座");
                // 查出本次选座的座位类型都有哪些列，用于计算所选座位与第一个座位的偏离值
                List<SeatColEnum> colEnumList = SeatColEnum.getColsByType(ticketReq0.getSeatTypeCode());
                LOG.info("本次选座的座位类型包含的列：{}", colEnumList);

                // 组成和前端两排选座一样的列表，用于作参照的座位列表，例：referSeatList = {A1, C1, D1, F1, A2, C2, D2, F2}
                List<String> referSeatList = new ArrayList<>();
                for(int i = 1;i <= 2;i++) {
                    for (SeatColEnum seatColEnum : colEnumList) {
                        referSeatList.add(seatColEnum.getCode() + i);
                    }
                }
                LOG.info("用于作参照的两排座位：{}", referSeatList);

                // 绝对偏移值，即：在参照座位列表中的位置
                List<Integer> aboluteOffsetList = new ArrayList<>();
                for (ConfirmOrderTicketReq ticket : tickets) {
                    aboluteOffsetList.add(referSeatList.indexOf(ticket.getSeat()));
                }
                LOG.info("计算得到所有座位的绝对偏移值：{}", aboluteOffsetList);
                // 相对偏移值，即：相对第一个所选位置的偏移量
                List<Integer> offsetList = new ArrayList<>();
                for (Integer index : aboluteOffsetList) {
                    offsetList.add(index - aboluteOffsetList.get(0));
                }
                LOG.info("计算得到所有座位的相对第一个座位的偏移值：{}", offsetList);

                getSeat(
                        finalSeatList,
                        date,
                        trainCode,
                        ticketReq0.getSeatTypeCode(),
                        ticketReq0.getSeat().split("")[0], // 从A1得到A
                        offsetList,
                        dailyTrainTicket.getStartIndex(),
                        dailyTrainTicket.getEndIndex()
                );

            }
            else {
                LOG.info("本次购票没有选座");
                for (ConfirmOrderTicketReq ticketReq : tickets) {
                    getSeat(
                            finalSeatList,
                            date,
                            trainCode,
                            ticketReq.getSeatTypeCode(),
                            null, // 从A1得到A
                            null,
                            dailyTrainTicket.getStartIndex(),
                            dailyTrainTicket.getEndIndex()
                    );
                }
            }

            //选座
            //一个车厢一个车厢的获取座位数据
            //挑选符合条件的座位，如果这个车厢不满足，则进入下一个车厢（多个选座应该在同一车厢）
            LOG.info("最终选座：{}", finalSeatList);

            //选中座位后事务处理
            //座位表修改售卖情况sell
            //余票详情表修改余票
            //为会员增加购票记录
            //更新确认订单为成功
            try {
                afterConfirmOrderService.afterDoConfirm(dailyTrainTicket, finalSeatList, tickets, confirmOrder);
            } catch (Exception e) {
                LOG.error("保存购票信息失败", e);
                throw new BusinessException(BusinessExceptionEnum.CONFIRM_ORDER_EXCEPTION);
            }
        } catch (InterruptedException e) {
            LOG.error("购票异常", e);
        } finally {
            LOG.info("购票流程结束，释放锁！");
            if (null != lock && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

    }

    private void getSeat(List<DailyTrainSeat> finalSeatList, Date date, String trainCode, String seatType, String column, List<Integer> offsetList, Integer startIndex, Integer endIndex) {
        List<DailyTrainSeat> getSeatList = new ArrayList<>();
        List<DailyTrainCarriage> carriageList = dailyTrainCarriageService.selectBySeatType(date, trainCode, seatType);
        LOG.info("共查出{}个符合条件的车厢", carriageList.size());

        for (DailyTrainCarriage dailyTrainCarriage : carriageList) {
            LOG.info("开始从车厢{}选座", dailyTrainCarriage.getIndex());
            List<DailyTrainSeat> seatList = dailyTrainSeatService.selectByCarriage(date, trainCode, dailyTrainCarriage.getIndex());
            LOG.info("车厢{}的座位数：{}", dailyTrainCarriage.getIndex(), seatList.size());
            for (DailyTrainSeat dailyTrainSeat : seatList) {
                getSeatList = new ArrayList<>();
                String col = dailyTrainSeat.getCol();
                Integer seatIndex = dailyTrainSeat.getCarriageSeatIndex();

                // 判断column，有值的话要比对列号
                if(StrUtil.isBlank(column)) {
                    LOG.info("无选座");
                }
                else {
                    //有选座判断列值是否对应
                    if(!column.equals(col)) {
                        LOG.info("座位{}列值不对，继续判断下一个座位，当前列值：{}，目标列值：{}", seatIndex, col, column);
                        continue;
                    }
                }

                // 判断当前座位不能被选中过
                boolean alreadyChooseFlag = false;
                for (DailyTrainSeat finalSeat : finalSeatList){
                    if (finalSeat.getId().equals(dailyTrainSeat.getId())) {
                        alreadyChooseFlag = true;
                        break;
                    }
                }
                if (alreadyChooseFlag) {
                    LOG.info("座位{}被选中过，不能重复选中，继续判断下一个座位", seatIndex);
                    continue;
                }

                // 判断座位是否可选
                boolean isChoose = canSell(dailyTrainSeat, startIndex, endIndex);
                if(isChoose) {
                    LOG.info("选中座位");
                    getSeatList.add(dailyTrainSeat);
                }
                else {
                    continue;
                }

                boolean isGetAllOffsetSeat = true;
                // 根据offset选剩下的座位
                if(CollUtil.isNotEmpty(offsetList)) {
                    LOG.info("有偏移值：{}，校验偏移的座位是否可选", offsetList);
                    // 从索引1开始，索引0就是当前已选中的票
                    for (int i = 1; i < offsetList.size(); i++) {
                        Integer offset = offsetList.get(i);
                        // 座位在库的索引是从1开始
                        Integer nextIndex = seatIndex + offset - 1;

                        // 有选座时，一定是在同一个车箱
                        if(nextIndex >= seatList.size()) {
                            LOG.info("座位{}不可选，偏移后的索引超出了这个车箱的座位数", nextIndex);
                            isGetAllOffsetSeat = false;
                            break;
                        }

                        //判断是否可选
                        DailyTrainSeat nextDailyTrainSeat = seatList.get(nextIndex);
                        boolean canSell = canSell(nextDailyTrainSeat, startIndex, endIndex);
                        if(!canSell) {
                            LOG.info("座位{}不可选", nextDailyTrainSeat.getCarriageSeatIndex());
                            isGetAllOffsetSeat = false;
                            break;
                        }
                        else {
                            LOG.info("座位{}被选中", nextDailyTrainSeat.getCarriageSeatIndex());
                            getSeatList.add(nextDailyTrainSeat);
                        }
                    }
                }
                if(!isGetAllOffsetSeat) {
                    continue;
                }

                // 保存选好的座位
                finalSeatList.addAll(getSeatList);
                return;
            }
        }
    }

    private boolean canSell(DailyTrainSeat dailyTrainSeat, Integer startIndex, Integer endIndex) {
        String sell = dailyTrainSeat.getSell();
        String sellPart = sell.substring(startIndex, endIndex);
        if(Integer.parseInt(sellPart) > 0) {
            LOG.info("座位{}在本次车站区间{}~{}已售过票，不可选中该座位", dailyTrainSeat.getCarriageSeatIndex(), startIndex, endIndex);
            return false;
        }
        else {
            LOG.info("座位{}在本次车站区间{}~{}未售过票，可选中该座位", dailyTrainSeat.getCarriageSeatIndex(), startIndex, endIndex);
            sellPart = sellPart.replaceAll("0", "1");
            sellPart = StrUtil.fillBefore(sellPart, '0', endIndex);
            sellPart = StrUtil.fillAfter(sellPart, '0', sell.length());

            // 当前区间售票信息sellPart 与库里的已售信息sell 按位或，即可得到该座位卖出此票后的售票详情
            int newSellInt = NumberUtil.binaryToInt(sellPart) | NumberUtil.binaryToInt(sell);
            String newSell = NumberUtil.getBinaryStr(newSellInt);
            LOG.info("座位{}被选中，原售票信息：{}，车站区间：{}~{}，即：{}，最终售票信息：{}"
                    , dailyTrainSeat.getCarriageSeatIndex(), sell, startIndex, endIndex, sellPart, newSell);
            dailyTrainSeat.setSell(newSell);
            return true;
        }
    }

    private static void reduceTickets(ConfirmOrderDoReq req, DailyTrainTicket dailyTrainTicket) {
        for (ConfirmOrderTicketReq ticket : req.getTickets()) {
            String seatTypeCode = ticket.getSeatTypeCode();
            SeatTypeEnum seatTypeEnum = EnumUtil.getBy(SeatTypeEnum::getCode, seatTypeCode);
            switch (seatTypeEnum) {
                case YDZ -> {
                    int countLeft = dailyTrainTicket.getYdz() - 1;
                    if(countLeft < 0) {
                        throw new BusinessException(BusinessExceptionEnum.CONFIRM_ORDER_TICKET_COUNT_ERROR);
                    }
                    dailyTrainTicket.setYdz(countLeft);
                }
                case EDZ -> {
                    int countLeft = dailyTrainTicket.getEdz() - 1;
                    if(countLeft < 0) {
                        throw new BusinessException(BusinessExceptionEnum.CONFIRM_ORDER_TICKET_COUNT_ERROR);
                    }
                    dailyTrainTicket.setEdz(countLeft);
                }
                case RW -> {
                    int countLeft = dailyTrainTicket.getRw() - 1;
                    if(countLeft < 0) {
                        throw new BusinessException(BusinessExceptionEnum.CONFIRM_ORDER_TICKET_COUNT_ERROR);
                    }
                    dailyTrainTicket.setRw(countLeft);
                }
                case YW -> {
                    int countLeft = dailyTrainTicket.getYw() - 1;
                    if(countLeft < 0) {
                        throw new BusinessException(BusinessExceptionEnum.CONFIRM_ORDER_TICKET_COUNT_ERROR);
                    }
                    dailyTrainTicket.setYw(countLeft);
                }
            }
        }
    }
}