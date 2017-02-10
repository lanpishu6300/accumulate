package me.ele.coffee.web.util;

import me.ele.contract.exception.ServerException;
import me.ele.contract.exception.ServiceException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by jiangzhiwen on 16/8/17.
 */
public class ServiceRetryUtil {
    public static Object retry(Object service, String funcName, Class<?>[] paramTypes, Object[] params) throws ServiceException, ServerException {
        return retry(service, funcName, paramTypes, params, 3);
    }
    public static Object retry(Object service, String funcName, Class<?>[] paramTypes, Object[] params, int retryCount) throws ServiceException, ServerException {
        Method method;
        try {
            method = service.getClass().getMethod(funcName, paramTypes);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        for(int i = 0; i < retryCount - 1; ++i){
            try {
                return invoke(service, method, params);
            }catch (ServiceException e){
                throw e;
            }catch (Exception e){
            }
        }
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
        }
        return invoke(service, method, params);
    }

    private static Object invoke(Object object, Method method, Object[] params) throws ServiceException, ServerException {
        try {
            return method.invoke(object, params);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            if(e.getTargetException() instanceof ServiceException){
                throw (ServiceException)e.getTargetException();
            }else if(e.getTargetException() instanceof ServerException){
                throw (ServerException)e.getTargetException();
            } else{
                throw new RuntimeException(e.getTargetException());
            }
        }
    }
}
