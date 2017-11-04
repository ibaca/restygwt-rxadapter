package com.intendia.gwt.restyrx.example.client;

import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestException;
import com.intendia.gwt.restyrx.client.RestyService;
import com.intendia.gwt.restyrx.client.RestyService.TypeMap;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import org.fusesource.restygwt.client.Dispatcher;
import org.fusesource.restygwt.client.Method;
import rx.Observable;

@RestyService(types = @TypeMap(type = Interface.class, with = Overlay.class))
@Path("/greeting-service")
public interface ObservableService {

    @PUT Observable<Void> ping();

    @GET Observable<Overlay> overlay();

    @POST Observable<Overlay> overlay(Overlay name);

    @GET Observable<Pojo> pojo();

    @POST Observable<Pojo> pojo(Pojo name);

    @GET Observable<Interface> iface();

    @POST Observable<Interface> iface(Interface name);

    @com.google.gwt.core.shared.GwtIncompatible Response gwtIncompatible();

    @com.google.common.annotations.GwtIncompatible("serverOnly") Response guavaIncompatible();

    class Factory {
        public static ObservableService create() {
            ObservableService_RestyAdapter service = new ObservableService_RestyAdapter();
            service.setDispatcher(new Dispatcher() {
                @Override public Request send(Method method, RequestBuilder builder) throws RequestException {
                    builder.setHeader("mode", "observable");
                    return builder.send();
                }
            });
            return service;
        }
    }
}
