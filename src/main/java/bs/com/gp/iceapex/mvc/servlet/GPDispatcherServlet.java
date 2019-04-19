package bs.com.gp.iceapex.mvc.servlet;

import bs.com.gp.iceapex.mvc.annotation.*;

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

public class GPDispatcherServlet extends HttpServlet {


    private static final String LOCATION = "contextConfigLocation";

    private static final String SCANPACKAGE = "scanPackage";

    private Properties prop = new Properties();

    private List<String> classNames = new ArrayList<String>();

    private Map<String,Object> ioc = new HashMap<String, Object>();

    private List<Handler> handlerMappings = new ArrayList<Handler>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
       try{
           doDispatcher(req,resp);
       }catch (Exception e){
           resp.getWriter().write("500 Exception,Details:\r\n" + Arrays.toString(e.getStackTrace()).replaceAll("\\[|\\]", "").replaceAll(",\\s", "\r\n"));
       }
    }

    private void doDispatcher(HttpServletRequest req, HttpServletResponse resp) throws Exception{
        try{

            Handler handler = getHandler(req);
            if(handler == null){
                resp.getWriter().write("404 Not Found !");
            }

            Class<?>[] parameterTypes = handler.method.getParameterTypes();

            Object[] paramValues = new Object[parameterTypes.length];

            Map<String,String[]> params = req.getParameterMap();

            for(Map.Entry<String,String[]> param : params.entrySet()){
                String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");

                //如果找到匹配的对象，则开始填充参数值
                if(!handler.paramIndexMapping.containsKey(param.getKey())){continue;}
                int index = handler.paramIndexMapping.get(param.getKey());
                paramValues[index] = convert(parameterTypes[index],value);
            }


            //设置方法中的request和response对象
            int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
            paramValues[reqIndex] = req;
            int respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
            paramValues[respIndex] = resp;

            handler.method.invoke(handler.controller, paramValues);


        }catch (Exception e){
            throw e;
        }

    }
    private Object convert(Class<?> type,String value){
        if(Integer.class == type){
            return Integer.valueOf(value);
        }
        return value;
    }
    private Handler getHandler(HttpServletRequest req) throws Exception {
        if(handlerMappings.isEmpty()){ return null; }

        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath, "").replaceAll("/+", "/");

        for (Handler handler : handlerMappings) {
            try{
                Matcher matcher = handler.pattern.matcher(url);
                //如果没有匹配上继续下一个匹配
                if(!matcher.matches()){ continue; }

                return handler;
            }catch(Exception e){
                throw e;
            }
        }
        return null;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        //1.加载配置文件
        doLoadConfig(config.getInitParameter(LOCATION));
        //2.扫描相关的类
        doScan(prop.getProperty(SCANPACKAGE));
        //3.初始化所有相关类，并保存到IOC容器中
        doInstance();
        //4.依赖注入
        doAutowired();
        //5.构造HandlerMapping
        doInitHandlerMapping();
        //6.等待请求，匹配url,定位方法，通过反射执行方法

        //提示信息
        System.out.println("Spring is init!");
    }

    private void doInitHandlerMapping() {
        if(ioc.isEmpty()){return;}

        for(Map.Entry<String,Object> entry : ioc.entrySet()){
            Class<?> clazz = entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(GPController.class)){
                continue;
            }

            String url = "";
            if(clazz.isAnnotationPresent(GPRequestMapping.class)){
                GPRequestMapping requestMapping = clazz.getAnnotation(GPRequestMapping.class);
                url = requestMapping.value();
            }

            Method[] methods = clazz.getMethods();

            for(Method method : methods){
               if(!method.isAnnotationPresent(GPRequestMapping.class)){
                   continue;
               }
                GPRequestMapping requstMapping = method.getAnnotation(GPRequestMapping.class);

                String regex =("/"+url+requstMapping.value()).replaceAll("/+","/");

                Pattern pattern = Pattern.compile(regex);

                handlerMappings.add(new Handler(pattern,entry.getValue(),method));

                System.out.println("mapped "+regex+"-->"+method+"");

            }


        }

    }

    private void doAutowired() {
        if(ioc.isEmpty()){return;}

        for (Map.Entry<String,Object> entry:ioc.entrySet() ) {

            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for(Field field : fields){
                if(!field.isAnnotationPresent(GPAtuowired.class)){
                    continue;
                }
                GPAtuowired autowired = field.getAnnotation(GPAtuowired.class);
                String beanName = autowired.value();
                if("".equals(beanName)){
                    beanName = field.getType().getName();
                }

                //强制访问
                field.setAccessible(true);
                try {
                    //TODO 断点调试看看输出
                    field.set(entry.getValue(),ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    private void doInstance() {

        if(classNames.size() == 0){return;}
        try{
            for (String className:classNames) {
                Class<?> clazz = Class.forName(className);
                if(clazz.isAnnotationPresent(GPController.class)){
                    String beanName = toLowerFirst(clazz.getSimpleName());
                    ioc.put(beanName,clazz.newInstance());
                }else if(clazz.isAnnotationPresent(GPService.class)){
                    GPService service = clazz.getAnnotation(GPService.class);

                    String beanName = service.value();
                    if("".equals(beanName.trim())){
                        beanName = toLowerFirst(clazz.getSimpleName());
                    }
                    ioc.put(beanName,clazz.newInstance());

                    for (Class<?> i:clazz.getInterfaces()) {
                        if(ioc.containsKey(i.getName())){
                            throw new Exception("The bean named "+i.getName()+" is already exists !");
                        }
                        ioc.put(i.getName(),clazz.newInstance());
                    }

                }else{
                    continue;
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    private String toLowerFirst(String simpleName) {
        char[] chars = simpleName.toCharArray();
        chars[0]+=32;
        return String.valueOf(chars);
    }

    private void doScan(String packageName) {
        URL url = this.getClass().getClassLoader().getResource("/"+packageName.replaceAll("\\.","/"));
        File dir = new File(url.getFile());
        for (File file: dir.listFiles()) {
            if(file.isDirectory()){
                doScan(packageName+"."+file.getName());
            }else{
                classNames.add(packageName+"."+file.getName().replace(".class","").trim());
            }
        }

    }

    private void doLoadConfig(String location) {

        InputStream is = null;
        try {
            is = this.getClass().getClassLoader().getResourceAsStream(location);
            prop.load(is);
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if(null != is){
                try {
                    is.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }


    }

    private class Handler{
        protected Object controller;	//保存方法对应的实例
        protected Method method;		//保存映射的方法
        protected Pattern pattern;
        protected Map<String,Integer> paramIndexMapping;	//参数顺序

        protected Handler(Pattern pattern,Object controller,Method method){
            this.controller = controller;
            this.method = method;
            this.pattern = pattern;

            paramIndexMapping = new HashMap<String,Integer>();
            putParamIndexMapping(method);
        }

        private void putParamIndexMapping(Method method){

            //提取方法中加了注解的参数
            Annotation[] [] pa = method.getParameterAnnotations();
            for (int i = 0; i < pa.length ; i ++) {
                for(Annotation a : pa[i]){
                    if(a instanceof GPRequestParam){
                        String paramName = ((GPRequestParam) a).value();
                        if(!"".equals(paramName.trim())){
                            paramIndexMapping.put(paramName, i);
                        }
                    }
                }
            }

            //提取方法中的request和response参数
            Class<?> [] paramsTypes = method.getParameterTypes();
            for (int i = 0; i < paramsTypes.length ; i ++) {
                Class<?> type = paramsTypes[i];
                if(type == HttpServletRequest.class ||
                        type == HttpServletResponse.class){
                    paramIndexMapping.put(type.getName(),i);
                }
            }
        }

    }
}
