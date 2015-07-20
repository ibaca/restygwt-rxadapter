# RestyGWT RxJava Adapter [![Build Status](https://travis-ci.org/ibaca/restygwt-rxadapter.svg)](https://travis-ci.org/ibaca/restygwt-rxadapter)

If you create this :worried:...
```java
@RestyService
@Path("/greeting-service")
public interface GreetingService {

    @POST Observable<Overlay> overlay(Overlay name);

    @POST Observable<Pojo> pojo(Pojo name);

    @POST Observable<Interop> interop(Interop name);

    static GreetingService create() { return new GreetingService_RestyAdapter();
}
```
This library generate this :open_mouth:...
```java
public class GreetingService_RestyAdapter extends AbstractAdapterService implements GreetingService {
  private GreetingService_RestyService service = GWT.create(GreetingService_RestyService.class);

  public GreetingService_RestyService service() { return service; }

  public Observable<Overlay> overlay(final Overlay name) {
    return Observable.create(s -> service().overlay(name, SubscriberMethodCallback.overlay(s))); 
  }

  public Observable<Pojo> pojo(final Pojo name) {
    return Observable.create(s -> service().pojo(name, SubscriberMethodCallback.method(s))); 
  }

  public Observable<Interop> interop(final Interop name) {
    return Observable.create(s -> service().interop(name, SubscriberMethodCallback.overlay(s))); 
  }
}
```
So you can do this :astonished:...
```
GreetingService.create().pojo(somePojo).subscribe(pojoWidget::show,pojoWidget::error);
```
