package com.study.spring.business.Controller;

import com.study.spring.business.service.IDemoService;
import com.study.spring.mvcframwork.annotation.MyAutowired;
import com.study.spring.mvcframwork.annotation.MyController;
import com.study.spring.mvcframwork.annotation.MyRequestMapping;
import com.study.spring.mvcframwork.annotation.MyRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@MyController
@MyRequestMapping("/demo")
public class DemoController {
    @MyAutowired
    private IDemoService demoService;

    @MyRequestMapping("/query")
    public void query(HttpServletRequest req, HttpServletResponse resp,
                      @MyRequestParam("name") String name){
        String result = "Hello, " + name;
        try {
            resp.getWriter().write(result);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @MyRequestMapping("/add")
    public void add(HttpServletRequest req, HttpServletResponse resp,
                    @MyRequestParam("a") Integer a, @MyRequestParam("b")Integer b){
        try {
            resp.getWriter().write(a + "+" + b + "=" + (a + b));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @MyRequestMapping("/remove")
    public void remove(HttpServletRequest req, HttpServletResponse resp,
                       @MyRequestParam("id") Integer id){

    }
}
