package com.intendia.gwt.restyrx.client;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface TypeMap {
    Class<?> type();

    Class<?> with();
}
