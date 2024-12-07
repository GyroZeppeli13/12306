package com.jiawa.train.business.mapper.cust;

import org.apache.ibatis.annotations.Param;

import java.util.Date;

public interface SkTokenMapperCust {

    int decrease(@Param("date") Date date, @Param("trainCode") String trainCode,@Param("decreaseCount") int decreaseCount);
}
