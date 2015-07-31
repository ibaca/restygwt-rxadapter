package org.fusesource.restygwt.examples.client;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import org.fusesource.restygwt.client.RestyService;
import org.fusesource.restygwt.client.RestyService.TypeMap;
import rx.Observable;

@RestyService(types = @TypeMap(type = Interface.class, with = Overlay.class))
@Path("/greeting-service")
public interface GreetingService {

    @PUT Observable<Void> ping();

    @GET Observable<Overlay> overlay();
    @POST Observable<Overlay> overlay(Overlay name);

    @GET Observable<Pojo> pojo();
    @POST Observable<Pojo> pojo(Pojo name);

    @GET Observable<Interop> interop();
    @POST Observable<Interop> interop(Interop name);

    @GET Observable<Interface> iface();
    @POST Observable<Interface> iface(Interface name);

    @com.google.gwt.core.shared.GwtIncompatible Response gwtIncompatible();
    @com.google.common.annotations.GwtIncompatible("serverOnly") Response guavaIncompatible();

    class Factory {
        public static GreetingService create() { return new GreetingService_RestyAdapter(); }
    }
}
