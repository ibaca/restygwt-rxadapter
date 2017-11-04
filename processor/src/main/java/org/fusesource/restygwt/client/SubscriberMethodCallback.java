package org.fusesource.restygwt.client;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.http.client.Request;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import rx.Observable;
import rx.Single;
import rx.SingleSubscriber;
import rx.Subscriber;
import rx.functions.Action0;
import rx.internal.util.BackpressureDrainManager;
import rx.internal.util.BackpressureDrainManager.BackpressureQueueCallback;
import rx.subscriptions.Subscriptions;

public abstract class SubscriberMethodCallback<T, R> implements MethodCallback<R>, BackpressureQueueCallback {

    public static <T> OverlayCallback<JsList<T>> overlay(final Subscriber<? super T> s) {
        return new OverlayCallbackAdapter<>(new SubscriberMethodCallback<T, JsList<T>>(s) {
            @Override public void onSuccess(Method method, final JsList<T> ts) {
                if (ts == null) response = Collections.emptyIterator();
                else response = ts.iterator();
                manager.drain();
            }
        });
    }

    public static <T> OverlayCallback overlay(final SingleSubscriber<? super T> s) {
        //noinspection unchecked Void do not extends JavaScriptObject
        return new OverlayCallbackAdapter(new MethodCallback<T>() {
            @Override public void onFailure(Method method, Throwable exception) {
                s.onError(exception);
            }

            @Override public void onSuccess(Method method, T response) {
                s.onSuccess(response);
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

    public static <T> MethodCallback<T> pojo(final SingleSubscriber<? super T> s) {
        return new MethodCallback<T>() {
            @Override public void onFailure(Method method, Throwable exception) {
                s.onError(exception);
            }

            @Override public void onSuccess(Method method, T response) {
                s.onSuccess(response);
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

        public final Iterator<T> iterator() {
            return new Iterator<T>() {
                int pos = 0;

                @Override public boolean hasNext() { return pos < length(); }

                @Override public T next() { return get(pos++); }

                @Override public void remove() { throw new UnsupportedOperationException("remove"); }
            };
        }
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

    public static abstract class RequestObservableOnSubscribe<T> implements Observable.OnSubscribe<T> {

        @Override public final void call(Subscriber<? super T> subscriber) {
            final Request request = request(subscriber);
            subscriber.add(Subscriptions.create(new Action0() {
                @Override public void call() {
                    request.cancel();
                }
            }));
        }

        protected abstract Request request(Subscriber<? super T> subscriber);
    }

    public static abstract class RequestSingleOnSubscribe<T> implements Single.OnSubscribe<T> {

        @Override public final void call(SingleSubscriber<? super T> subscriber) {
            final Request request = request(subscriber);
            subscriber.add(Subscriptions.create(new Action0() {
                @Override public void call() {
                    request.cancel();
                }
            }));
        }

        protected abstract Request request(SingleSubscriber<? super T> subscriber);
    }
}
