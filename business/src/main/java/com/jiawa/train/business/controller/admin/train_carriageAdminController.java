package com.jiawa.train.business.controller.admin;;
  
import com.jiawa.train.common.context.LoginMemberContext;
import com.jiawa.train.common.resp.CommonResp;
import com.jiawa.train.common.resp.PageResp;
import com.jiawa.train.business.req.train_carriageQueryReq;
import com.jiawa.train.business.req.train_carriageSaveReq;
import com.jiawa.train.business.resp.train_carriageQueryResp;
import com.jiawa.train.business.service.train_carriageService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/train-carriage")
public class train_carriageAdminController {

    @Resource
    private train_carriageService train_carriageService;
    
    @PostMapping("/save")
    public CommonResp<Object> save(@Valid @RequestBody train_carriageSaveReq req) {
      train_carriageService.save(req);
      return new CommonResp<>();
    }
    
    @GetMapping("/query-list")
    public CommonResp<PageResp<train_carriageQueryResp>> queryList(@Valid train_carriageQueryReq req) {
      PageResp<train_carriageQueryResp> list = train_carriageService.queryList(req);
      return new CommonResp<>(list);
    }
    
    @DeleteMapping("/delete/{id}")
    public CommonResp<Object> delete(@PathVariable Long id) {
      train_carriageService.delete(id);
      return new CommonResp<>();
    }

}
