package com.intendia.gwt.restyrx.example.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.text.shared.AbstractRenderer;
import com.google.gwt.text.shared.Renderer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.TextBox;
import org.fusesource.restygwt.client.Resource;
import org.fusesource.restygwt.client.RestServiceProxy;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Action1;

public class Example implements EntryPoint {

    public void onModuleLoad() {
        Resource resource = new Resource(GWT.getModuleBaseURL() + "greeting-service");

        final ObservableService oService = ObservableService.Factory.create();
        ((RestServiceProxy) oService).setResource(resource);

        final SingleService sService = SingleService.Factory.create();
        ((RestServiceProxy) sService).setResource(resource);

        RootPanel.get().add(new Label("Name:"));
        final TextBox nameInput = new TextBox();
        RootPanel.get().add(nameInput);
        nameInput.addValueChangeHandler(new ValueChangeHandler<String>() {
            @Override public void onValueChange(ValueChangeEvent<String> event) {
                Example.this.getCustomGreeting(oService, nameInput.getValue());
            }
        });
        nameInput.setValue("ping", true);

        oService.ping().doOnTerminate(new Action0() {
            @Override public void call() { RootPanel.get().add(new Label("observable pong")); }
        }).subscribe();
        sService.ping().subscribe(new Action1<Void>() {
            @Override public void call(Void n) { RootPanel.get().add(new Label("single pong")); }
        });
    }

    private void getCustomGreeting(ObservableService service, String name) {
        Overlay overlay = (Overlay) JavaScriptObject.createObject();
        overlay.setStr(name);
        service.overlay(overlay).subscribe(subscriber("overlays", new AbstractRenderer<Overlay>() {
            public String render(Overlay object) {
                return object.getStr();
            }
        }));

        Pojo pojo = new Pojo();
        pojo.setStr(name);
        service.pojo(pojo).subscribe(subscriber("pojo", new AbstractRenderer<Pojo>() {
            public String render(Pojo object) { return object.getStr(); }
        }));

        Interface iface = (Overlay) JavaScriptObject.createObject();
        iface.setStr(name);
        service.iface(iface).subscribe(subscriber("iface", new AbstractRenderer<Interface>() {
            public String render(Interface object) { return object.getStr(); }
        }));
    }

    private <T> Subscriber<T> subscriber(final String container, final Renderer<T> render) {
        return new Subscriber<T>() {
            @Override
            public void onCompleted() { }

            @Override
            public void onError(Throwable e) { Window.alert("Error: " + e); }

            @Override
            public void onNext(T t) {
                RootPanel.get().add(new Label("server said using " + container + ": " + render.render(t)));
            }
        };
    }
}
