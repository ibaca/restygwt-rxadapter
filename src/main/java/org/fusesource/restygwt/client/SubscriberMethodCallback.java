package org.fusesource.restygwt.client;

import java.util.Iterator;
import java.util.List;
import rx.Subscriber;
import rx.internal.util.BackpressureDrainManager;
import rx.internal.util.BackpressureDrainManager.BackpressureQueueCallback;

public class SubscriberMethodCallback<T> implements MethodCallback<List<T>>, BackpressureQueueCallback {

    public static <T> OverlayCallback<List<T>> overlay(Subscriber<? super T> s) {
        return new OverlayMethodCallback<>(new SubscriberMethodCallback<>(s));
    }

    public static <T> MethodCallback<List<T>> method(Subscriber<? super T> s) {
        return new SubscriberMethodCallback<>(s);
    }

    private final Subscriber<? super T> child;
    private final BackpressureDrainManager manager;
    private Iterator<T> response;
    private T peek;

    public SubscriberMethodCallback(Subscriber<? super T> child) {
        (this.child = child).setProducer(manager = new BackpressureDrainManager(this));
    }

    @Override
    public void onFailure(Method method, Throwable exception) {
        manager.terminateAndDrain(exception);
    }

    @Override
    public void onSuccess(Method method, List<T> response) {
        this.response = response.iterator();
        manager.drain();
    }

    @Override
    public Object peek() {
        if (peek != null) {
            return peek;
        } else if (response != null && response.hasNext()) {
            return peek = response.next();
        } else {
            return null;
        }
    }

    @Override
    public Object poll() {
        T pool = peek;
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
}
