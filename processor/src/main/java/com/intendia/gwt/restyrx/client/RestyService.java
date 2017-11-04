package com.intendia.gwt.restyrx.client;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface RestyService {

    TypeMap[] types() default {};

    @Target({})
    @Retention(RetentionPolicy.RUNTIME)
    @interface TypeMap {
        Class<?> type();

        Class<?> with();
    }
}
