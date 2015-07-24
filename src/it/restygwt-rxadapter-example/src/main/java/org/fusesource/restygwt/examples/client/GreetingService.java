package org.fusesource.restygwt.examples.client;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;

import org.fusesource.restygwt.client.RestyService;
import rx.Observable;

@RestyService
@Path("/greeting-service")
public interface GreetingService {

    @PUT Observable<Void> ping();

    @GET Observable<Overlay> overlay();
    @POST Observable<Overlay> overlay(Overlay name);

    @GET Observable<Pojo> pojo();
    @POST Observable<Pojo> pojo(Pojo name);

    @GET Observable<Interop> interop();
    @POST Observable<Interop> interop(Interop name);

    class Factory {
        public static GreetingService create() { return new GreetingService_RestyAdapter(); }
    }
}
