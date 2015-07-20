package org.fusesource.restygwt.examples.client;

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

public class UI implements EntryPoint {

    public void onModuleLoad() {
        RootPanel.get().add(new Label("Name:"));
        final TextBox nameInput = new TextBox();
        RootPanel.get().add(nameInput);
        nameInput.addValueChangeHandler(new ValueChangeHandler<String>() {
            @Override
            public void onValueChange(ValueChangeEvent<String> event) {
                getCustomGreeting(nameInput.getValue());
            }
        });
        nameInput.setValue("ping", true);
    }

    private void getCustomGreeting(String name) {
        GreetingService service = GreetingService.Factory.create();
        Resource resource = new Resource(GWT.getModuleBaseURL() + "greeting-service");
        ((RestServiceProxy) service).setResource(resource);

        Overlay overlay = (Overlay) JavaScriptObject.createObject();
        overlay.setStr(name);
        service.overlay(overlay).subscribe(subscriber("overlays", new AbstractRenderer<Overlay>() {
            public String render(Overlay object) {
                return object.getStr();
            }
        }));

        Interop interop = createInterop();
        interop.setStr(name);
        service.interop(interop).subscribe(subscriber("interop", new AbstractRenderer<Interop>() {
            public String render(Interop object) {
                return object.getStr();
            }
        }));

        Pojo pojo = new Pojo();
        pojo.setStr(name);
        service.pojo(pojo).subscribe(subscriber("pojo", new AbstractRenderer<Pojo>() {
            public String render(Pojo object) {
                return object.getStr();
            }
        }));
    }

    private <T> Subscriber<T> subscriber(final String container, final Renderer<T> render) {
        return new Subscriber<T>() {
            @Override
            public void onCompleted() { }

            @Override
            public void onError(Throwable e) {
                Window.alert("Error: " + e);
            }

            @Override
            public void onNext(T t) {
                RootPanel.get().add(new Label("server said using " + container + ": " + render.render(t)));
            }
        };
    }

    static native Interop createInterop() /*-{
        return {};
    }-*/;
}
