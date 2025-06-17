package com.ll.framework.ioc;
import com.ll.framework.ioc.annotations.Bean;
import com.ll.framework.ioc.annotations.Component;
import com.ll.standard.util.Ut;
import org.reflections.Reflections;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ApplicationContext {
    String beanPackage;
    Map<String, Object> beans;
    Reflections reflections;
    public ApplicationContext(String basePackage) {
        this.beanPackage = basePackage;
        reflections = new Reflections(basePackage);
    }

    /**
     * bean을 찾는다.
     */
    public void init() {
        beans = new HashMap<>();
        addComponents();
        getBeans();
    }

    /**
     * bean 어노테이션이 달린 메소드를 찾아서 반환값을 beans에 넣는다.
     */
    private void getBeans(){
        List<Class<?>> classes = getClassesHasAnnotatedMethods(Bean.class);
        classes.forEach(this::makeBean);
        for (Class<?> clazz : classes) {
            Object self = getBean(clazz);
            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {
                Parameter[] parameters = method.getParameters();
                List<Object> values = new ArrayList<>();
                for (Parameter parameter : parameters) {
                    Class<?> type = parameter.getType();
                    if (!hasBean(type)) {
                        getBeans(type);
                    }
                    values.add(getBean(type));
                }
                try{
                    Object returnValue = method.invoke(self,values.toArray());
                    Class<?> returnType = method.getReturnType();
                    String methodName = method.getName();
                    putBean(methodName, returnValue);
                    putBean(returnType, returnValue);

                }catch (Exception e){
                    System.out.println(e);
                }

            }

        }

    }

    /**
     * 메소드를 실행하는 코드
     * 매개변수가 필요한데 해당 객체가 없으면 재귀로 작동함
     * Bean 어노테이션을 한 메소드에게서 필요한 Bean의 값을 얻을 수 있다면 그 메소드를 실행시킴
     * @param type
     */
    private void getBeans( Class<?> type) {
        try{
            List<Class<?>> classes = getClassesHasAnnotatedMethods(Bean.class);
            classes.forEach(this::makeBean);
            for (Class<?> clazz : classes) {
                Object self = getBean(clazz);
                Method[] methods = clazz.getDeclaredMethods();
                for (Method method : methods) {
                    Class<?> methodType = method.getReturnType();
                    if (type != methodType) {
                        continue;
                    }
                    Parameter[] parameters = method.getParameters();
                    List<Object> values = new ArrayList<>();
                    for (Parameter parameter : parameters) {
                        Class<?> parameterType = parameter.getType();
                        if (!hasBean(parameterType)) {
                            getBeans(parameterType);
                        }
                        values.add(getBean(parameterType));
                    }
                    Object returnValue = method.invoke(self, values.toArray());
                    Class<?> returnType = method.getReturnType();
                    String methodName = method.getName();
                    putBean(methodName, returnValue);
                    putBean(returnType, returnValue);
                }
            }
        } catch (Exception e){
            System.out.println(e);
        }
    }

    /**
     * 클래스 생성하는 코드
     * @param clazz
     */
    private void makeBean(Class<?> clazz){
        if (hasBean(clazz)) {
            return;
        }
        try{
            List<Object> values = new ArrayList<>();
            Constructor<?>[] constructors = clazz.getConstructors();
            for (Constructor<?> constructor : constructors) {
                // Component를 어노테이션한 클래스 만으로 생성자를 생성 가능한지?
                boolean found = false;
                Parameter[] parameters = constructor.getParameters();
                for (Parameter parameter : parameters) {
                    Class<?> parameterType = parameter.getType();
                    if (findAnnotation(parameterType, Component.class)){
                        if (!hasBean(parameterType)) {
                            makeBean(parameterType);
                        }
                    }else{
                        found = true;
                    }
                }
                if (found) {
                    continue;
                }
                for (Parameter parameter : parameters) {
                    Class<?> type = parameter.getType();
                    values.add(getBean(type));
                }
                Object obj = constructor.newInstance(values.toArray(new Object[values.size()]));
                putBean(clazz, obj);
                break;
            }

        } catch (Exception e) {

        }

    }

    /**
     * 메소드의 함수명으로 요청하는 경우도 있고 클래스의 이름으로 요청하는 경우가 있어서 둘 다 만듬
     * @param clazz
     * @param bean
     */
    private void putBean(Class<?> clazz, Object bean) {
        beans.put(Ut.str.lcfirst(clazz.getSimpleName()), bean);
    }
    private void putBean(String key, Object value){ beans.put(key, value);}

    private Object getBean(Class<?> clazz) {
        return beans.get(Ut.str.lcfirst(clazz.getSimpleName()));
    }

    public <T> T genBean(String beanName) {
        return (T) beans.get(beanName);
    }

    private boolean hasBean(Class<?> clazz) {
        return beans.containsKey(Ut.str.lcfirst(clazz.getSimpleName()));
    }

    private void addComponents() {
        try{
            List<Class<?>> classes = getAnnotatedClasses( Component.class);
            for (Class<?> clazz : classes) {
                if (!hasBean(clazz)) {
                    makeBean(clazz);
                }
            }
        }catch (Exception e){
            System.out.println(e.getMessage());
        }
    }


    /**
     * 어노테이션에서 어노테이션을 찾음
     * @param clazz
     * @param annotation
     * @return
     */
    private boolean findAnnotation(Class<?> clazz, Class<? extends Annotation> annotation) {
        if (clazz.isAnnotationPresent(annotation)) {
            return true;
        }
        Annotation[] annotations = clazz.getAnnotations();
        for (Annotation annotation1 : annotations) {
            if (annotation1.annotationType().equals(clazz)) {
                return false;
            }
            // 재귀로 어노테이션을 추적함
            boolean result = findAnnotation(annotation1.annotationType(), annotation);
            if (result) {
                return true;
            }
        }
        return false;
    }

    /**
     * 의외로 쓸데가 있는거 같아서 사용 중
     * @param packageName
     * @return
     */
    private List<Class<?>> getClasses(String packageName) {
        List<Class<?>> classes = new ArrayList<Class<?>>();
        String packagePath = packageName.replace('.', '/');
        URL resource = ClassLoader.getSystemClassLoader().getResource(packagePath);

        if (resource == null) {
            return classes;
        }
        File dir = new File(resource.getPath());
        if (!dir.exists() && !dir.isDirectory()) {
            return classes;
        }
        try{
            List<String> paths = Files.walk(Paths.get(dir.getPath()))
                    .filter((path) -> path.getFileName().toString().endsWith(".class")).map((Path::toString)).toList();
            for (String path : paths) {
                int index = path.indexOf(packageName.replace('.', '\\'));
                String className = path.substring(index, path.length() - 6);
                try{
                    Class<?> clazz= Class.forName(className.replace('\\', '.'));
                    classes.add(clazz);
                }catch (ClassNotFoundException e) {

                }
            }

        }catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return classes;
    }

    /**
     * 클래스에 어노테이션이 달린 경우
     * @param annotation
     * @return
     */
    private List<Class<?>> getAnnotatedClasses(Class<? extends Annotation> annotation) {
        return getClasses(beanPackage)
                .stream()
                .filter(
                        (clazz)->
                                findAnnotation(clazz, annotation)
                ).toList();
    }


    /**
     * 아래 메소드 stream 깔끔하게 처리하려고 만든 함수
     * @param clazz
     * @param annotation
     * @return
     */
    private boolean hasAnnotatedMethods(Class<?> clazz, Class<? extends Annotation> annotation) {
        for (Method method : clazz.getDeclaredMethods()) {
            for (Annotation annotation1 : method.getAnnotations()) {
                if (annotation1.annotationType().equals(annotation)) {
                    return true;
                }
                if (findAnnotation(annotation1.annotationType(), annotation)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 클래스의 메소드에 해당 어노테이션이 달린 경우
     * @param annotation
     * @return
     */
    private List<Class<?>> getClassesHasAnnotatedMethods(Class<? extends Annotation> annotation) {
        return getClasses(beanPackage)
                .stream()
                .filter(
                        (cls)->
                                hasAnnotatedMethods(cls,annotation)
                ).toList();
    }
}
