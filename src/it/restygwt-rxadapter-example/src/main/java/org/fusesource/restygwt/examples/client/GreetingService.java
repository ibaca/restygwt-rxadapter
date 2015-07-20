package org.fusesource.restygwt.examples.client;

import javax.ws.rs.POST;
import javax.ws.rs.Path;

import org.fusesource.restygwt.client.RestyService;
import rx.Observable;

@RestyService
@Path("/greeting-service")
public interface GreetingService {

    @POST Observable<Overlay> overlay(Overlay name);

    @POST Observable<Pojo> pojo(Pojo name);

    @POST Observable<Interop> interop(Interop name);

    class Factory {
        public static GreetingService create() { return new GreetingService_RestyAdapter(); }
    }
}
