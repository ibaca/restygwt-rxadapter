package org.fusesource.restygwt.examples.client;

import com.google.gwt.core.client.js.JsProperty;
import com.google.gwt.core.client.js.JsType;

@JsType
public interface Interop {

    @JsProperty
    String getStr();

    @JsProperty
    void setStr(String str);
}
