package com.jiawa.train.business.req;

import com.jiawa.train.common.req.PageReq;
import lombok.Data;

@Data
public class DailyTrainSeatQueryReq extends PageReq {
    private String trainCode;
}