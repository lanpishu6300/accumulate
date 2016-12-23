package me.ele.bpm.family.admin.restaurant.utils;

import com.vividsolutions.jts.util.CollectionUtil;
import me.ele.contract.exception.ServiceException;
import me.ele.elog.Log;
import me.ele.elog.LogFactory;
import me.ele.family.agent.model.PageData;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.util.GenericSignature;
import org.slf4j.Logger;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Map;

/**
 * Created by lanpishu on 16/8/24.
 */
    @Aspect
    public class MethodLogger {
    private Log log = LogFactory.getLog(MethodLogger.class);
    @Around("execution (* me.ele.bpm.family.admin.*.service.impl.*.*(..))")
        public Object around(ProceedingJoinPoint point) throws ServiceException {
            String className = point.getTarget().getClass().getName();
            String methodName = MethodSignature.class.cast(point.getSignature()).getMethod().getName();
            Object[] args = point.getArgs();
          //  String argsString = "";
            StringBuilder stringBuilder = new StringBuilder();
            if(args.length!=0) {
                for (Object arg : args) {
                    stringBuilder.append(arg+"_");
                }
            }
            log.info("enter method " + className + "_" +methodName + "|" +"params :" + stringBuilder.toString());


            Object result = null;
            try {
                result = point.proceed();
            } catch (ServiceException exception){
                throw exception;
            }catch (Throwable throwable) {
                log.error("service method failed at: {}" , throwable.getStackTrace());
                throwable.printStackTrace();
                return null;
            }

            String resultString = "";
            if(result instanceof Collection ){
                resultString = ((Collection) result).size()+"";
            }else if(result instanceof Map){
                resultString =((Map) result).size() + "";
            }else if(result instanceof PageData || result instanceof me.ele.bpm.family.admin.restaurant.pojo.PageData){
                resultString = "return pagedData " + result.getClass().getName();
            }else if(result != null){
                resultString = result.toString();
            }
            log.info("exit method "+ className + "_" +methodName + "|" + "method result " + resultString);
            return result;
        }
    }



