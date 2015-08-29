package org.fusesource.restygwt.client;

import com.google.gwt.core.client.JavaScriptObject;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import rx.Subscriber;
import rx.internal.util.BackpressureDrainManager;
import rx.internal.util.BackpressureDrainManager.BackpressureQueueCallback;

public abstract class SubscriberMethodCallback<T, R> implements MethodCallback<R>, BackpressureQueueCallback {

    public static <T> OverlayCallback<JsList<T>> overlay(Subscriber<? super T> s) {
        return new OverlayCallbackAdapter<>(new SubscriberMethodCallback<T, JsList<T>>(s) {
            @Override public void onSuccess(Method method, final JsList<T> ts) {
                if (ts == null) response = Collections.emptyIterator();
                else response = new Iterator<T>() {
                    int pos = 0;

                    @Override public boolean hasNext() { return pos < ts.length(); }

                    @Override public T next() { return ts.get(pos++); }

                    @Override public void remove() { throw new UnsupportedOperationException("remove"); }
                };
                manager.drain();
            }
        });
    }

    public static <T> MethodCallback<List<T>> pojo(Subscriber<? super T> s) {
        return new SubscriberMethodCallback<T, List<T>>(s) {
            @Override public void onSuccess(Method method, List<T> ts) {
                this.response = ts.iterator();
                manager.drain();
            }
        };
    }

    private final Subscriber<? super T> child;
    protected final BackpressureDrainManager manager;
    protected Iterator<T> response;
    private T peek;

    public SubscriberMethodCallback(Subscriber<? super T> child) {
        (this.child = child).setProducer(manager = new BackpressureDrainManager(this));
    }

    @Override
    public void onFailure(Method method, Throwable exception) {
        manager.terminateAndDrain(exception);
    }

    @Override
    public T peek() {
        if (response != null && peek == null) {
            if (response.hasNext()) {
                peek = response.next();
            } else {
                manager.terminate();
                child.onCompleted();
            }
        }
        return peek;
    }

    @Override
    public Object poll() {
        T pool = peek();
        peek = null;
        return pool;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean accept(Object value) {
        if (child.isUnsubscribed()) {
            return true;
        }
        child.onNext((T) value);
        return false;
    }

    @Override
    public void complete(Throwable exception) {
        if (exception != null) {
            child.onError(exception);
        } else {
            child.onCompleted();
        }
    }

    public static class JsList<T> extends JavaScriptObject {
        protected JsList() { }

        public final native T get(int index) /*-{
            return this[index];
        }-*/;

        public final native int length() /*-{
            return this.length;
        }-*/;
    }

    private static final class OverlayCallbackAdapter<T extends JavaScriptObject> implements OverlayCallback<T> {
        private final MethodCallback<T> adaptee;

        private OverlayCallbackAdapter(MethodCallback<T> adaptee) {
            this.adaptee = adaptee;
        }

        @Override public void onFailure(Method method, Throwable exception) {
            adaptee.onFailure(method, exception);
        }

        @Override public void onSuccess(Method method, T response) {
            adaptee.onSuccess(method, response);
        }
    }
}
