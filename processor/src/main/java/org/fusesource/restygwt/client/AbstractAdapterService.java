package org.fusesource.restygwt.client;

import org.fusesource.restygwt.client.callback.CallbackAware;

public abstract class AbstractAdapterService implements CallbackAware, RestServiceProxy {
    private MethodCallback callback;

    public abstract RestService service();

    public final void setResource(Resource resource) {
        ((RestServiceProxy) service()).setResource(resource);
    }

    public final Resource getResource() {
        return ((RestServiceProxy) service()).getResource();
    }

    public final void setDispatcher(Dispatcher resource) {
        ((RestServiceProxy) service()).setDispatcher(resource);
    }

    public final Dispatcher getDispatcher() {
        return ((RestServiceProxy) service()).getDispatcher();
    }

    public void setCallback(MethodCallback callback) {
        this.callback = callback;
    }

    public MethodCallback verifyCallback(String methodName) {
        if (this.callback == null) throw new IllegalArgumentException("You need to call this service with " +
                "REST.withCallback(new MethodCallback<..>(){..}).call(service)." + methodName + "(..) and " +
                "not try to access the service directly");
        return callback;
    }
}
