package org.ddd.example.adapter.portal.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.ddd.example.share.dto.ResponseData;
import org.springframework.web.bind.annotation.*;

/**
 * TODO 后续可以考虑基于Command和Query的注解，自动生成Controller
 * @author <template/>
 * @date
 */
@Tag(name="测试控制器")
@RestController
@RequestMapping(value = "/appApi/test")
@Slf4j
public class TestAppController {

    @GetMapping("")
    public ResponseData<String> test(){
        return ResponseData.success("hello world");
    }

}
