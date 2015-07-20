package org.fusesource.restygwt.examples.client;

import com.google.gwt.core.client.JavaScriptObject;

public class Overlay extends JavaScriptObject {

    protected Overlay() {
    }

    public final native String getStr() /*-{
        return this.str;
    }-*/;

    public final native void setStr(String str) /*-{
        this.str = str;
    }-*/;
}
