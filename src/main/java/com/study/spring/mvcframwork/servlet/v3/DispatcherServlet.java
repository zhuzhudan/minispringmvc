package com.study.spring.mvcframwork.servlet.v3;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DispatcherServlet extends HttpServlet {

    //保存一个url和Method的关系
    public class Handler{
        //必须把url放到HandlerMapping中，才可形成对应关系，而不是把对应关系强加给Map
        private Pattern urlPattern;
        private Method method;
        private Object controller;
        private Class<?>[] paramTypes;

        public Class<?>[] getParamTypes() {
            return paramTypes;
        }

        public Pattern getUrlPattern() {

            return urlPattern;
        }

        public Method getMethod() {
            return method;
        }

        public Object getController() {
            return controller;
        }

        //形参列表，参数的名字作为key，参数的顺序，位置作为值
        private Map<String, Integer> paramIndexMapping;

        public Handler(Pattern urlPattern, Object controller, Method method) {
            this.urlPattern = urlPattern;
            this.method = method;
            this.controller = controller;

            paramTypes = method.getParameterTypes();

            paramIndexMapping = new HashMap<>();
            putParamIndexMapping(method);
        }


        private void putParamIndexMapping(Method method) {
            //提取方法中加了注解的参数
            Annotation[][] pa = method.getParameterAnnotations();
            for (int i = 0; i < pa.length; i++){
                for (Annotation annotation : pa[i]){
                    if(annotation instanceof MyRequestParam){
                        String paramName = ((MyRequestParam)annotation).value();
                        if(!"".equals((paramName.trim()))){
                            paramIndexMapping.put(paramName, i);
                        }
                    }
                }
            }

            //提取方法中的request和response参数
            Class<?>[] paramTypes = method.getParameterTypes();
            for (int i = 0; i < paramTypes.length; i++){
                Class<?> type = paramTypes[i];
                if(type == HttpServletResponse.class || type == HttpServletRequest.class){
                    paramIndexMapping.put(type.getName(), i);
                }
            }

        }
    }
    //保存application.properties配置文件中的内容
    private Properties contextConfig = new Properties();

    //保存扫描的所有类名
    private List<String> classNames = new ArrayList<>();

    //IOC容器
    private Map<String, Object> ioc = new HashMap<>();

    //Map的key只能是url
    //handlerMapping功能是把url和method对应关系，已经具备Map的功能
    private List<Handler> handlerMapping = new ArrayList<Handler>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

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
        Handler handler = getHandler(req);
        if(handler == null){
            resp.getWriter().write("404 Not Found");
            return;
        }

        //获得方法的形参列表
        Class<?>[] paramTypes = handler.getParamTypes();
        Object[] paramValues = new Object[paramTypes.length];

        Map<String, String[]> params= req.getParameterMap();
        for (Map.Entry<String, String[]> param : params.entrySet()) {
            String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]","").replaceAll("\\s","");
            if(!handler.paramIndexMapping.containsKey(param.getKey())){continue;}

            int index = handler.paramIndexMapping.get(param.getKey());
            paramValues[index] = convert(paramTypes[index], value);
        }

        if(handler.paramIndexMapping.containsKey(HttpServletRequest.class.getName())){
            int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
            paramValues[reqIndex] = req;
        }

        if(handler.paramIndexMapping.containsKey(HttpServletResponse.class.getName())){
            int respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
            paramValues[respIndex] = resp;
        }

        Object returnValue = handler.method.invoke(handler.controller, paramValues);
        if(returnValue == null || returnValue instanceof Void){return;}
        resp.getWriter().write(returnValue.toString());

    }

    private Handler getHandler(HttpServletRequest req) {
        if(handlerMapping.isEmpty()){return null;}
        //获取绝对路径
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        //将绝对路径转化成相对路径
        url = url.replaceAll(contextPath, "").replaceAll("/+", "/");

        for (Handler handler : this.handlerMapping){
            Matcher matcher = handler.getUrlPattern().matcher(url);
            if(!matcher.matches()){continue; }
            return handler;

        }
        return null;
    }

    private Object convert(Class<?> type, String value){
        if(Integer.class == type){
            return Integer.valueOf(value);
        } else if (Double.class == type){
            return Double.valueOf(value);
        }
        return value;
    }

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

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();

            if(!clazz.isAnnotationPresent(MyController.class)){continue;}

            String baseUrl = "";
            if(clazz.isAnnotationPresent(MyRequestMapping.class)){
                MyRequestMapping requestMapping = clazz.getAnnotation(MyRequestMapping.class);
                baseUrl = requestMapping.value();
            }

            for (Method method : clazz.getMethods()) {
                if(!method.isAnnotationPresent(MyRequestMapping.class)){continue;}

                MyRequestMapping requestMapping = method.getAnnotation(MyRequestMapping.class);
                String regex = ("/" + baseUrl + "/" + requestMapping.value()).replaceAll("/+", "/");
                Pattern pattern = Pattern.compile(regex);
                handlerMapping.add(new Handler(pattern, entry.getValue(), method));
            }

        }

    }

    /**
     * 依赖注入
     */
    private void doAutowired() {
        if(ioc.isEmpty()){return;}

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            //获取所有字段
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (!field.isAnnotationPresent(MyAutowired.class)){continue;}

                MyAutowired autowired = field.getAnnotation(MyAutowired.class);

                String beanName = autowired.value();
                if("".equals(beanName)){
                    beanName = field.getType().getName();
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
        if (classNames.isEmpty()) {return; }

        try {
            for (String className : classNames) {
                Class<?> clazz = Class.forName(className);
                if(clazz.isAnnotationPresent(MyController.class)){
                    Object instance = clazz.newInstance();
                    String beanName = toLowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName, instance);
                } else if(clazz.isAnnotationPresent(MyService.class)){
                    MyService service = clazz.getAnnotation(MyService.class);
                    String beanName = service.value();
                    if("".equals(beanName)){
                        beanName = toLowerFirstCase(clazz.getSimpleName());
                    }

                    Object instance = clazz.newInstance();
                    ioc.put(beanName, instance);

                    for (Class<?> i : clazz.getInterfaces()){
                        if(ioc.containsKey(i.getName())){
                            throw new Exception("The \"" + i.getName() + "\" is exists");
                        }
                        ioc.put(i.getName(), instance);
                    }
                } else {
                    continue;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
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
        File classPath = new File(url.getFile());
        for (File file : classPath.listFiles()) {
            if(file.isDirectory()){
                doScanner(scanPackage + "." + file.getName());
            } else {
                if(!file.getName().endsWith(".class")){continue;}
                String className = scanPackage + "." + file.getName().replaceAll(".class", "");
                classNames.add(className);
            }
        }

    }

    /**
     * 1、加载配置文件
     * @param contextConfigLocation
     */
    private void doLoadConfig(String contextConfigLocation) {
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
