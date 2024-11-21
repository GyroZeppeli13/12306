package com.jiawa.train.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.ObjectUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jiawa.train.common.resp.PageResp;
import com.jiawa.train.common.util.SnowUtil;
import com.jiawa.train.business.domain.train_seat;
import com.jiawa.train.business.domain.train_seatExample;
import com.jiawa.train.business.mapper.train_seatMapper;
import com.jiawa.train.business.req.train_seatQueryReq;
import com.jiawa.train.business.req.train_seatSaveReq;
import com.jiawa.train.business.resp.train_seatQueryResp;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class train_seatService {

    private static final Logger LOG = LoggerFactory.getLogger(train_seatService.class);

    @Resource
    private train_seatMapper train_seatMapper;

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
        train_seatExample.setOrderByClause("id desc");
        train_seatExample.Criteria criteria = train_seatExample.createCriteria();

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
}