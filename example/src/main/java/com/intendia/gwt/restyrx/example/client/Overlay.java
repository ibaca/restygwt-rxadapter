package com.intendia.gwt.restyrx.example.client;

import com.google.gwt.core.client.JavaScriptObject;

public class Overlay extends JavaScriptObject implements Interface {

    protected Overlay() {
    }

    public final native String getStr() /*-{
        return this.str;
    }-*/;

    public final native void setStr(String str) /*-{
        this.str = str;
    }-*/;
}
