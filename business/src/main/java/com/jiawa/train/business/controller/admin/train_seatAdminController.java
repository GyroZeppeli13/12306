package com.jiawa.train.business.controller.admin;;
  
import com.jiawa.train.common.context.LoginMemberContext;
import com.jiawa.train.common.resp.CommonResp;
import com.jiawa.train.common.resp.PageResp;
import com.jiawa.train.business.req.train_seatQueryReq;
import com.jiawa.train.business.req.train_seatSaveReq;
import com.jiawa.train.business.resp.train_seatQueryResp;
import com.jiawa.train.business.service.train_seatService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/train-seat")
public class train_seatAdminController {

    @Resource
    private train_seatService train_seatService;
    
    @PostMapping("/save")
    public CommonResp<Object> save(@Valid @RequestBody train_seatSaveReq req) {
      train_seatService.save(req);
      return new CommonResp<>();
    }
    
    @GetMapping("/query-list")
    public CommonResp<PageResp<train_seatQueryResp>> queryList(@Valid train_seatQueryReq req) {
      PageResp<train_seatQueryResp> list = train_seatService.queryList(req);
      return new CommonResp<>(list);
    }
    
    @DeleteMapping("/delete/{id}")
    public CommonResp<Object> delete(@PathVariable Long id) {
      train_seatService.delete(id);
      return new CommonResp<>();
    }

}
