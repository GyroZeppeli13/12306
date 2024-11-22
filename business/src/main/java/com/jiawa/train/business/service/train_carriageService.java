package com.jiawa.train.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.ObjectUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jiawa.train.business.domain.train_carriage;
import com.jiawa.train.business.domain.train_carriageExample;
import com.jiawa.train.business.enums.SeatColEnum;
import com.jiawa.train.business.mapper.train_carriageMapper;
import com.jiawa.train.business.req.train_carriageQueryReq;
import com.jiawa.train.business.req.train_carriageSaveReq;
import com.jiawa.train.business.resp.train_carriageQueryResp;
import com.jiawa.train.common.resp.PageResp;
import com.jiawa.train.common.util.SnowUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class train_carriageService {

    private static final Logger LOG = LoggerFactory.getLogger(train_carriageService.class);

    @Resource
    private train_carriageMapper train_carriageMapper;

    public void save(train_carriageSaveReq req) {
        DateTime now = DateTime.now();

        // 自动计算出列数和总座位数
        List<SeatColEnum> seatColEnums = SeatColEnum.getColsByType(req.getSeatType());
        req.setColCount(seatColEnums.size());
        req.setSeatCount(req.getColCount() * req.getRowCount());

        train_carriage trainCarriage = BeanUtil.copyProperties(req, train_carriage.class);
        if (ObjectUtil.isNull(trainCarriage.getId())) {
            trainCarriage.setId(SnowUtil.getSnowflakeNextId());
            trainCarriage.setCreateTime(now);
            trainCarriage.setUpdateTime(now);
            train_carriageMapper.insert(trainCarriage);
        } else {
            trainCarriage.setUpdateTime(now);
            train_carriageMapper.updateByPrimaryKey(trainCarriage);
        }
    }


    public PageResp<train_carriageQueryResp> queryList(train_carriageQueryReq req) {
        train_carriageExample train_carriageExample = new train_carriageExample();
        train_carriageExample.setOrderByClause("train_code asc, `index` asc");
        train_carriageExample.Criteria criteria = train_carriageExample.createCriteria();
        if (ObjectUtil.isNotEmpty(req.getTrainCode())) {
            criteria.andTrainCodeEqualTo(req.getTrainCode());
        }

        LOG.info("查询页码：{}", req.getPage());
        LOG.info("每页条数：{}", req.getSize());
        PageHelper.startPage(req.getPage(), req.getSize());
        List<train_carriage> train_carriageList = train_carriageMapper.selectByExample(train_carriageExample);

        PageInfo<train_carriage> pageInfo = new PageInfo<>(train_carriageList);
        LOG.info("总行数：{}", pageInfo.getTotal());
        LOG.info("总页数：{}", pageInfo.getPages());

        List<train_carriageQueryResp> list = BeanUtil.copyToList(train_carriageList, train_carriageQueryResp.class);

        PageResp<train_carriageQueryResp> pageResp = new PageResp<>();
        pageResp.setTotal(pageInfo.getTotal());
        pageResp.setList(list);
        return pageResp;
    }

    public void delete(Long id) {
        train_carriageMapper.deleteByPrimaryKey(id);
    }

    public List<train_carriage> selectByTrainCode(String trainCode) {
        train_carriageExample trainCarriageExample = new train_carriageExample();
        trainCarriageExample.setOrderByClause("`index` asc");
        train_carriageExample.Criteria criteria = trainCarriageExample.createCriteria();
        criteria.andTrainCodeEqualTo(trainCode);
        return train_carriageMapper.selectByExample(trainCarriageExample);
    }

}