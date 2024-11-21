package com.jiawa.train.business.mapper;

import com.jiawa.train.business.domain.train_carriage;
import com.jiawa.train.business.domain.train_carriageExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface train_carriageMapper {
    long countByExample(train_carriageExample example);

    int deleteByExample(train_carriageExample example);

    int deleteByPrimaryKey(Long id);

    int insert(train_carriage record);

    int insertSelective(train_carriage record);

    List<train_carriage> selectByExample(train_carriageExample example);

    train_carriage selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") train_carriage record, @Param("example") train_carriageExample example);

    int updateByExample(@Param("record") train_carriage record, @Param("example") train_carriageExample example);

    int updateByPrimaryKeySelective(train_carriage record);

    int updateByPrimaryKey(train_carriage record);
}