package com.jiawa.train.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jiawa.train.business.domain.train_carriage;
import com.jiawa.train.business.domain.train_seat;
import com.jiawa.train.business.domain.train_seatExample;
import com.jiawa.train.business.enums.SeatColEnum;
import com.jiawa.train.business.mapper.train_seatMapper;
import com.jiawa.train.business.req.train_seatQueryReq;
import com.jiawa.train.business.req.train_seatSaveReq;
import com.jiawa.train.business.resp.train_seatQueryResp;
import com.jiawa.train.common.resp.PageResp;
import com.jiawa.train.common.util.SnowUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class train_seatService {

    private static final Logger LOG = LoggerFactory.getLogger(train_seatService.class);

    @Resource
    private train_seatMapper train_seatMapper;

    @Resource
    private train_carriageService train_carriageService;

    public void save(train_seatSaveReq req) {
        DateTime now = DateTime.now();
        train_seat train_seat = BeanUtil.copyProperties(req, train_seat.class);
        if (ObjectUtil.isNull(train_seat.getId())) {
            train_seat.setId(SnowUtil.getSnowflakeNextId());
            train_seat.setCreateTime(now);
            train_seat.setUpdateTime(now);
            train_seatMapper.insert(train_seat);
        } else {
            train_seat.setUpdateTime(now);
            train_seatMapper.updateByPrimaryKey(train_seat);
        }
    }

    public PageResp<train_seatQueryResp> queryList(train_seatQueryReq req) {
        train_seatExample train_seatExample = new train_seatExample();
        train_seatExample.setOrderByClause("train_code asc, carriage_index asc, carriage_seat_index asc");
        train_seatExample.Criteria criteria = train_seatExample.createCriteria();
        if (ObjectUtil.isNotEmpty(req.getTrainCode())) {
            criteria.andTrainCodeEqualTo(req.getTrainCode());
        }

        LOG.info("查询页码：{}", req.getPage());
        LOG.info("每页条数：{}", req.getSize());
        PageHelper.startPage(req.getPage(), req.getSize());
        List<train_seat> train_seatList = train_seatMapper.selectByExample(train_seatExample);

        PageInfo<train_seat> pageInfo = new PageInfo<>(train_seatList);
        LOG.info("总行数：{}", pageInfo.getTotal());
        LOG.info("总页数：{}", pageInfo.getPages());

        List<train_seatQueryResp> list = BeanUtil.copyToList(train_seatList, train_seatQueryResp.class);

        PageResp<train_seatQueryResp> pageResp = new PageResp<>();
        pageResp.setTotal(pageInfo.getTotal());
        pageResp.setList(list);
        return pageResp;
    }

    public void delete(Long id) {
        train_seatMapper.deleteByPrimaryKey(id);
    }

    @Transactional
    public void genTrainSeat(String trainCode) {
        DateTime now = DateTime.now();
        // 清空当前车次下的所有的座位记录
        train_seatExample trainSeatExample = new train_seatExample();
        train_seatExample.Criteria criteria = trainSeatExample.createCriteria();
        criteria.andTrainCodeEqualTo(trainCode);
        train_seatMapper.deleteByExample(trainSeatExample);

        // 查找当前车次下的所有的车厢
        List<train_carriage> trainCarriages = train_carriageService.selectByTrainCode(trainCode);
        LOG.info("当前车次下的车厢数：{}", trainCarriages.size());

        // 循环生成每个车厢的座位
        for (train_carriage trainCarriage : trainCarriages) {
            // 拿到车厢数据：行数、座位类型(得到列数)
            Integer rowCount = trainCarriage.getRowCount();
            String seatType = trainCarriage.getSeatType();
            int seatIndex = 1;

            // 根据车厢的座位类型，筛选出所有的列，比如车箱类型是一等座，则筛选出columnList={ACDF}
            List<SeatColEnum> colEnumList = SeatColEnum.getColsByType(seatType);
            LOG.info("根据车厢的座位类型，筛选出所有的列：{}", colEnumList);

            // 循环行数
            for (int row = 1; row <= rowCount; row++) {
                // 循环列数
                for (SeatColEnum seatColEnum : colEnumList) {
                    // 构造座位数据并保存数据库
                    train_seat trainSeat = new train_seat();
                    trainSeat.setId(SnowUtil.getSnowflakeNextId());
                    trainSeat.setTrainCode(trainCode);
                    trainSeat.setCarriageIndex(trainCarriage.getIndex());
                    trainSeat.setRow(StrUtil.fillBefore(String.valueOf(row), '0', 2));
                    trainSeat.setCol(seatColEnum.getCode());
                    trainSeat.setSeatType(seatType);
                    trainSeat.setCarriageSeatIndex(seatIndex++);
                    trainSeat.setCreateTime(now);
                    trainSeat.setUpdateTime(now);
                    train_seatMapper.insert(trainSeat);
                }
            }
        }
    }

    public List<train_seat> selectByTrainCode(String trainCode) {
        train_seatExample trainSeatExample = new train_seatExample();
        trainSeatExample.setOrderByClause("`id` asc");
        train_seatExample.Criteria criteria = trainSeatExample.createCriteria();
        criteria.andTrainCodeEqualTo(trainCode);
        return train_seatMapper.selectByExample(trainSeatExample);
    }

}