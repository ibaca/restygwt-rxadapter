package org.fusesource.restygwt.examples.client;

import com.google.gwt.core.client.js.JsProperty;
import com.google.gwt.core.client.js.JsType;

@JsType
public interface Interop {

    @JsProperty String getStr();

    @JsProperty void setStr(String str);

    @JsProperty JsList<String> getStrArr();

    @JsProperty Integer[] getIntArr();

    // Simple collection interfaces required because standard collections are not supported
    // by JsInterop. You must crete your own JsTypes, and implement a server version.
    @JsType interface JsList<T> {
        @JsProperty int getLength();

        /** Adds one or more elements to the end of an array and returns the new length of the array. */
        void push(T obj);

        /** Removes the last element from an array and returns that element. */
        T pop();

        class Static {
            public static native <T> JsList<T> newInstance() /*-{
                return [];
            }-*/;
        }
    }
}
