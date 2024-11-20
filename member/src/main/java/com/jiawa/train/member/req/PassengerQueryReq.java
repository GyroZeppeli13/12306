package com.jiawa.train.member.req;

import com.jiawa.train.common.req.PageReq;
import lombok.Data;

@Data
public class PassengerQueryReq extends PageReq {

    Long memberId;
}