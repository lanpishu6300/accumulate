package me.ele.breaker.metric;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicLong;

import me.ele.breaker.BreakerProperty;
import me.ele.breaker.BreakerStatus;
import me.ele.breaker.metric.MetricsBucketArray.MetricSnapshot;
//import me.ele.elog.Log;
//import me.ele.elog.LogFactory;

public class BreakerMetrics  {
  //  private final static Log logger = LogFactory.getLog(BreakerMetrics.class);
    private final String apiName;
    private final BreakerProperty property;
    private final MetricsBucketArray array;
    private AtomicLong lastSingleTestTime = new AtomicLong();
    private volatile boolean open = false;
    private volatile boolean inTestPhase = false;

    public BreakerMetrics(String apiName, BreakerProperty property) {
        this.apiName = apiName;
        this.property = property;
        this.array = new MetricsBucketArray(property.getBucketNumber());
    }

    public void calc() {
        MetricSnapshot check = array.calcCheck();
        if (open && (check.getCircuitBreak() + check.getTotal()) < 1.0f) {
          //  logger.error("Api({}), Action(Close), Info(No request for a while)!", apiName);
            open = false;
            return;

        }
        if (open) {
            return;
        }

        if (check.getTotal() >= property.getRequestCountThreshold()) {
            float total = check.getTotal();
            float error = check.getError();
            float errorPercentage = error / total;
            if (errorPercentage > property.getErrorPercentageThreshold()) {
                lastSingleTestTime = new AtomicLong(System.currentTimeMillis());
//                logger.error(
//                        "Api({}), Action(Open), Info(Error percentage: {}, Total request: {}, all requests will try to invoke fallback instead, single test will start after {}ms)!",
//                        apiName, errorPercentage, total, property.getSingleTestWindowInMillis());
                open = true;
            } else {
//                logger.debug("Api({}), Action(Check), Info(Error percentage: {}, Total request: {})!", apiName, errorPercentage, total);
            }
        }
        System.out.println("calculator = " + (array));
    }

    public void increment(BreakerStatus status) {
        array.peek().increment(status);
    }

    public boolean isOpen() {
        if (open && !letSingleTest()) {
            return true;
        } else {
            return false;
        }
    }

    public void singleTestPass(boolean flag) {
        inTestPhase = false;
        if (flag) {
//            logger.error("Api({}), Action(RECOVER_SUCCEED), Info(Single test passed)!", apiName);
            open = false;
        } else {
//            logger.error("Api({}), Action(RECOVER_FAIL), Info(Single test failed)!", apiName);
        }
    }

    public boolean inTestPhase() {
        return inTestPhase;
    }

    private boolean letSingleTest() {
        long last = lastSingleTestTime.get();
        long next = last + property.getSingleTestWindowInMillis();
        long now = System.currentTimeMillis();
        if (now > next && lastSingleTestTime.compareAndSet(last, now)) {
            inTestPhase = true;
            return inTestPhase;
        } else {
            return false;
        }
    }

    @SuppressWarnings("serial")
//    @Override
    public Object dumpInfo() {
        return new LinkedHashMap<String, Object>() {
            {
                put("isOpen", open);
                put("lastSingleTestTime", DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(new Timestamp(lastSingleTestTime.get()).toLocalDateTime()));
                MetricSnapshot check = array.getLastCheck();
                put("errorCount", ((int) check.getError()));
                put("totalCount", ((int) check.getTotal()));
                put("circuitBreakCount", ((int) check.getCircuitBreak()));
            }
        };
    }
}
