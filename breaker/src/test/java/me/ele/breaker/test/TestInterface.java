package me.ele.breaker.test;

public interface TestInterface {
    default String testWithFallBack() {
        return "fallback";
    }

    String testWithoutFallBack();
}
