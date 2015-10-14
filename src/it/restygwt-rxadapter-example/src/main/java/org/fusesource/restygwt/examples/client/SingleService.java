package org.fusesource.restygwt.examples.client;

import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestException;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import org.fusesource.restygwt.client.Dispatcher;
import org.fusesource.restygwt.client.Method;
import org.fusesource.restygwt.client.RestyService;
import org.fusesource.restygwt.client.RestyService.TypeMap;
import rx.Single;

@RestyService(types = @TypeMap(type = Interface.class, with = Overlay.class))
@Path("/greeting-service")
public interface SingleService {

    @PUT Single<Void> ping();

    @GET Single<Overlay> overlay();

    @POST Single<Overlay> overlay(Overlay name);

    @GET Single<Pojo> pojo();

    @POST Single<Pojo> pojo(Pojo name);

    @GET Single<Interface> iface();

    @POST Single<Interface> iface(Interface name);

    @com.google.gwt.core.shared.GwtIncompatible Response gwtIncompatible();

    @com.google.common.annotations.GwtIncompatible("serverOnly") Response guavaIncompatible();

    class Factory {
        public static SingleService create() {
            SingleService_RestyAdapter service = new SingleService_RestyAdapter();
            service.setDispatcher(new Dispatcher() {
                @Override public Request send(Method method, RequestBuilder builder) throws RequestException {
                    builder.setHeader("mode", "single");
                    return builder.send();
                }
            });
            return service;
        }
    }
}
