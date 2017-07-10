package me.ele.breaker;


import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class Fallback {
    private static Constructor<Lookup> constructor;

    static {
        try {
            constructor = Lookup.class.getDeclaredConstructor(Class.class, int.class);
            constructor.setAccessible(true);
        } catch (NoSuchMethodException | SecurityException e) {
           // throw new Exception(e);
        }
    }

    private final boolean supportsFallback;
    private final MethodHandle methodHandle;

    public Fallback(Method method) throws Exception {
        try {
            supportsFallback = method.isDefault();
            if (supportsFallback) {
                final Class<?> declaringClass = method.getDeclaringClass();
                methodHandle = constructor.newInstance(declaringClass, Lookup.PRIVATE).unreflectSpecial(method, declaringClass)
                        .bindTo(Proxy.newProxyInstance(declaringClass.getClassLoader(), new Class[] { declaringClass }, (_proxy, _method, _args) -> null));
            } else {
                methodHandle = null;
            }
        } catch (ReflectiveOperationException t) {
            throw new Exception(t);
        }
    }

    public boolean supportsFallback() {
        return supportsFallback;
    }

    public Object callFallback(Object[] args) throws Throwable {
        return methodHandle.invokeWithArguments(args);
    }
}
