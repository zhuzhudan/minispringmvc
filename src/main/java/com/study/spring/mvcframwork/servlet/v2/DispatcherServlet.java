package com.study.spring.mvcframwork.servlet.v2;

import com.study.spring.mvcframwork.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

//已可以应用
//用到的设计模式：工厂模式、单例模式、委派模式、策略模式、模板模式
public class DispatcherServlet extends HttpServlet {
    //保存application.properties配置文件中的内容
    private Properties contextConfig = new Properties();

    //保存扫描的所有类名
    private List<String> classNames = new ArrayList<>();

    //IOC容器
    private Map<String, Object> ioc = new HashMap<>();

    //保存url和method的对应关系，策略模式
    private Map<String, Method> handlerMapping = new HashMap<>();

    //模板模式
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    //模板模式
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        //6、调用，运行阶段
        try {
            doDispatch(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Excetion, Detail : "+ Arrays.toString(e.getStackTrace()));
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        //获取绝对路径
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        //将绝对路径转化成相对路径
        url = url.replaceAll(contextPath, "").replaceAll("/+", "/");

        if(!this.handlerMapping.containsKey(url)){
            resp.getWriter().write("404 Not Found");
            return;
        }

        Method method = this.handlerMapping.get(url);
        //保存请求的url参数列表
        Map<String, String[]> params = req.getParameterMap();
        //获取方法的形参列表
        Class<?>[] parameterTypes = method.getParameterTypes();
        //赋值参数的位置
        Object[] paramValues = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Class parameterType = parameterTypes[i];
            if (parameterType == HttpServletRequest.class) {
                paramValues[i] = req;
                continue;
            } else if (parameterType == HttpServletResponse.class) {
                paramValues[i] = resp;
                continue;
            }
            //拿到所有参数包括HttpServletRequest和HttpServletResponse
            Annotation[][] pa = method.getParameterAnnotations();
            //只取当前顺序下的那个参数
            for (Annotation a : pa[i]) {
                if (a instanceof MyRequestParam) {
                    String paramName = ((MyRequestParam) a).value();
                    if (params.containsKey(paramName)) {
                        for (Map.Entry<String, String[]> param : params.entrySet()) {
                            if (param.getKey().equals(paramName)) {
                                String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll("\\s", ",");

                                paramValues[i] = convert(parameterType, value);
                                break;
                            }
                        }
                    }
                }
            }

//                MyRequestParam requestParam = (MyRequestParam)parameterType.getAnnotation(MyRequestParam.class);
//                if(params.containsKey(requestParam.value())){
//                    for (Map.Entry<String, String[]> param : params.entrySet()){
//                        String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll("\\s", ",");
//                        paramValues[i] = value;
//                    }
//                }

        }


        //通过反射拿到method所在的class，拿到class之后拿到class的名称
        //再调用toLowerFirstCase获得beanName
        String beanName = toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        //第一个参数：方法所在的实例
        //第二个参数：调用时所需要的实参
        method.invoke(ioc.get(beanName), paramValues);
    }

    private Object convert(Class<?> type, String value){
        if(Integer.class == type){
            return Integer.valueOf(value);
        }
        return value;
    }

    //模板模式
    @Override
    public void init(ServletConfig config) throws ServletException {
        //1、加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        //2、扫描相关的类
        doScanner(contextConfig.getProperty("scanPackage"));

        //3、初始化扫描到的类，并且将他们放入到IOC容器中
        doInstance();

        //4、完成依赖注入
        doAutowired();

        //5、初始化HandlerMapping
        initHandlerMapping();

    }

    /**
     * 初始化url和Method方法的一一对应关系
     */
    private void initHandlerMapping() {
        if(ioc.isEmpty()){return;}
        for (Map.Entry<String, Object> entry : ioc.entrySet()){
            Class<?> clazz = entry.getValue().getClass();

            if(!clazz.isAnnotationPresent(MyController.class)){continue;}

            //记录写在类上的@MyRequestMapping("/demo")
            String baseUrl = "";
            MyRequestMapping requestMapping = clazz.getAnnotation(MyRequestMapping.class);
            baseUrl= requestMapping.value();

            //默认获取所有的public方法
            for (Method method : clazz.getMethods()) {
                if(!method.isAnnotationPresent(MyRequestMapping.class)){continue;}

                requestMapping = method.getAnnotation(MyRequestMapping.class);
                String url = (baseUrl + "/" + requestMapping.value()).replaceAll("/+", "/");
                handlerMapping.put(url, method);

            }

        }
    }

    /**
     * 依赖注入
     */
    private void doAutowired() {
        if(ioc.isEmpty()){return;}

        for (Map.Entry<String, Object> entry : ioc.entrySet()){
            //Declared 所有的字段，包括private、protected、default
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields){
                if(!field.isAnnotationPresent(MyAutowired.class)){continue;}
                MyAutowired autowired = field.getAnnotation(MyAutowired.class);

                //若没有自定义的beanNam，根据类型注入
                String beanName = autowired.value().trim();
                if("".equals(beanName)){
                    //获得接口类型，作为key
                    beanName = field.getType().getName();
                } else if(!beanName.contains(".")){
                    beanName = toUpperFirstCase(beanName);
                }

                field.setAccessible(true);

                try {
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private String toUpperFirstCase(String simpleName) {
        char[] chars = simpleName.toCharArray();
        chars[0] -= 32;
        return String.valueOf(chars);
    }

    /**
     * 初始化扫描到的类，并且将他们放入到IOC容器中
     */
    private void doInstance() {
        //初始化，为DI做准备
        if(classNames.isEmpty()){return;}

        for (String className : classNames){
            try {
                //只有加了注解的类，才初始化
                Class<?> clazz = Class.forName(className);
                Object instance = clazz.newInstance();
                if(clazz.isAnnotationPresent(MyController.class)){
                    String beanName = toLowerFirstCase(clazz.getSimpleName());//获取类名
                    ioc.put(beanName, instance);
                } else if(clazz.isAnnotationPresent(MyService.class)) {
                    //1、自定义的beanName
                    MyService service = clazz.getAnnotation(MyService.class);
                    String beanName = service.value();
                    //2、默认类名首字母小写
                    if("".equals(beanName)) {
                        beanName = toLowerFirstCase(clazz.getSimpleName());
                    }
                    ioc.put(beanName, instance);
                    //3、需要根据类型自动赋值
                    for (Class<?> c : clazz.getInterfaces()){//获取类的接口
                        if(ioc.containsKey((c.getName()))){
                            throw new Exception("The \"" + c.getName() + "\" is exists");
                        }
                        ioc.put(c.getName(), instance);
                    }

                } else {
                    continue;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private String toLowerFirstCase(String simpleName){
        char[] chars = simpleName.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    /**
     * 2、扫描相关的类
     * @param scanPackage
     */
    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
        File classPathFile = new File(url.getFile());
        for (File file : classPathFile.listFiles()){
            if(file.isDirectory()){
                doScanner(scanPackage + "." + file.getName());
            } else {
                if(!file.getName().endsWith(".class")){continue;}
                String className = scanPackage + "."+ file.getName().replace(".class", "");
                classNames.add(className);
            }
        }
    }

    /**
     * 1、加载配置文件
     * @param contextConfigLocation
     */
    private void doLoadConfig(String contextConfigLocation) {
        //直接从类路径下找到Spring主配置文件所在的路径
        //并将其独处来放到Properties对象中
        InputStream stream = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            contextConfig.load(stream);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(stream != null){
                try {
                    stream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
