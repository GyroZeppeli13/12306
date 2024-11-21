package com.jiawa.train.business.mapper;

import com.jiawa.train.business.domain.train_seat;
import com.jiawa.train.business.domain.train_seatExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface train_seatMapper {
    long countByExample(train_seatExample example);

    int deleteByExample(train_seatExample example);

    int deleteByPrimaryKey(Long id);

    int insert(train_seat record);

    int insertSelective(train_seat record);

    List<train_seat> selectByExample(train_seatExample example);

    train_seat selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") train_seat record, @Param("example") train_seatExample example);

    int updateByExample(@Param("record") train_seat record, @Param("example") train_seatExample example);

    int updateByPrimaryKeySelective(train_seat record);

    int updateByPrimaryKey(train_seat record);
}