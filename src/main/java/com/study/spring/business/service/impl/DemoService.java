package com.study.spring.business.service.impl;

import com.study.spring.business.service.IDemoService;
import com.study.spring.mvcframwork.annotation.MyService;

@MyService
public class DemoService implements IDemoService {
    @Override
    public String get(String name) {
        return "Hello, " + name;
    }
}
