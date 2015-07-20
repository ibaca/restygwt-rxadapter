package org.fusesource.restygwt.processor;

import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.common.collect.FluentIterable.from;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.service.AutoService;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.js.JsType;
import com.google.gwt.core.shared.GWT;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;
import org.fusesource.restygwt.client.AbstractAdapterService;
import org.fusesource.restygwt.client.MethodCallback;
import org.fusesource.restygwt.client.OverlayCallback;
import org.fusesource.restygwt.client.RestService;
import org.fusesource.restygwt.client.RestyService;
import org.fusesource.restygwt.client.SubscriberMethodCallback;
import rx.Observable;
import rx.Subscriber;

@AutoService(Processor.class)
@SupportedOptions({"debug", "verify"})
public class RestyServiceAdapterProcessor extends AbstractProcessor {

    @Override
    public ImmutableSet<String> getSupportedAnnotationTypes() {
        return ImmutableSet.of(RestyService.class.getName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try {
            return processImpl(annotations, roundEnv);
        } catch (Exception e) {
            // We don't allow exceptions of any kind to propagate to the compiler
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            fatalError(writer.toString());
            return true;
        }
    }

    private boolean processImpl(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) throws Exception {
        if (!roundEnv.processingOver()) {
            processAnnotations(annotations, roundEnv);
        }

        return true;
    }

    private void processAnnotations(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) throws Exception {
        Filer filer = processingEnv.getFiler();

        List<? extends TypeElement> elements = from(roundEnv.getElementsAnnotatedWith(RestyService.class))
                .filter(TypeElement.class)
                .filter(new Predicate<TypeElement>() {
                    public boolean apply(TypeElement input) { return input.getKind().isInterface(); }
                })
                .toList();

        log(annotations.toString());
        log(elements.toString());

        for (TypeElement restService : elements) {
            final AnnotationMirror annotation = MoreElements.getAnnotationMirror(restService, RestyService.class).get();

            ClassName serviceName = ClassName.get(restService);
            log("service interface: " + serviceName);

            ClassName restyName = ClassName.get(serviceName.packageName(), serviceName.simpleName() + "_RestyService");
            log("service resty interface: " + restyName);

            ClassName adapterName = ClassName.get(serviceName.packageName(), serviceName.simpleName() + "_RestyAdapter");
            log("service resty adapter: " + adapterName);

            final TypeSpec.Builder restyBuilder = TypeSpec.interfaceBuilder(restyName.simpleName())
                    .addAnnotations(transformAnnotations(restService.getAnnotationMirrors()))
                    .addOriginatingElement(restService)
                    .addModifiers(Modifier.PUBLIC)
                    .addSuperinterface(RestService.class);

            final TypeSpec.Builder adapterBuilder = TypeSpec.classBuilder(adapterName.simpleName())
                    .addOriginatingElement(restService)
                    .addModifiers(Modifier.PUBLIC)
                    .superclass(AbstractAdapterService.class)
                    .addSuperinterface(TypeName.get(restService.asType()));

            adapterBuilder.addField(FieldSpec
                    .builder(restyName, "service", Modifier.PRIVATE)
                    .initializer("$T.create($T.class)", GWT.class, restyName)
                    .build());
            adapterBuilder.addMethod(MethodSpec.methodBuilder("service")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(restyName)
                    .addStatement("return service")
                    .build());

            final FluentIterable<? extends ExecutableElement> methods = from(restService.getEnclosedElements())
                    .filter(new Predicate<Element>() {
                        public boolean apply(Element input) { return input.getKind() == ElementKind.METHOD; }
                    })
                    .filter(ExecutableElement.class);
            for (ExecutableElement method : methods) {
                if (!MoreTypes.isTypeOf(Observable.class, method.getReturnType())) {
                    error("Observable<T> return type required", method, annotation);
                    continue;
                }
                final TypeMirror observableType = asDeclared(method.getReturnType()).getTypeArguments().get(0);
                final String parameterNames = from(method.getParameters())
                        .transform(new Function<VariableElement, String>() {
                            public String apply(VariableElement input) { return input.getSimpleName().toString(); }
                        })
                        .join(Joiner.on(", "));

                // Resty methods
                TypeName listType = ParameterizedTypeName.get(ClassName.get(List.class), TypeName.get(observableType));
                restyBuilder.addMethod(MethodSpec
                        .methodBuilder(method.getSimpleName().toString())
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .addAnnotations(transformAnnotations(method.getAnnotationMirrors()))
                        .addParameters(from(method.getParameters())
                                .transform(new Function<VariableElement, ParameterSpec>() {
                                    public ParameterSpec apply(VariableElement p) {
                                        return ParameterSpec
                                                .builder(TypeName.get(p.asType()), p.getSimpleName().toString())
                                                .addAnnotations(transformAnnotations(p.getAnnotationMirrors()))
                                                .build();
                                    }
                                }))
                        .addParameter(isOverlay(observableType)
                                ? ParameterizedTypeName.get(ClassName.get(OverlayCallback.class), listType)
                                : ParameterizedTypeName.get(ClassName.get(MethodCallback.class), listType), "callback")
                        .build());

                // Adapter methods
                adapterBuilder.addMethod(MethodSpec
                        .methodBuilder(method.getSimpleName().toString())
                        .addModifiers(Modifier.PUBLIC)
                        .returns(TypeName.get(method.getReturnType()))
                        .addParameters(from(method.getParameters())
                                .transform(new Function<VariableElement, ParameterSpec>() {
                                    public ParameterSpec apply(VariableElement p) {
                                        return ParameterSpec
                                                .builder(TypeName.get(p.asType()), p.getSimpleName().toString())
                                                .addModifiers(Modifier.FINAL)
                                                .addAnnotations(transformAnnotations(p.getAnnotationMirrors()))
                                                .build();
                                    }
                                }))
                        .addStatement("" +
                                        "return $1T.create(new $2T<$3T>() {\n" +
                                        "  public void call($4T<? super $3T> subscription) {\n" +
                                        "    service().$5L($6L, $7T.$8L(subscription));\n" +
                                        "  }\n" +
                                        "})",
                                ClassName.get(Observable.class),
                                ClassName.get(Observable.OnSubscribe.class),
                                ClassName.get(observableType),
                                ClassName.get(Subscriber.class),
                                method.getSimpleName().toString(),
                                parameterNames,
                                ClassName.get(SubscriberMethodCallback.class),
                                isOverlay(observableType) ? "overlay" : "method"
                        ).build());
            }

            final Writer serviceOut = filer.createSourceFile(restyName.toString()).openWriter();
            JavaFile.builder(serviceName.packageName(), restyBuilder.build()).build().writeTo(serviceOut);
            serviceOut.close();

            final Writer adapterOut = filer.createSourceFile(adapterName.toString()).openWriter();
            JavaFile.builder(serviceName.packageName(), adapterBuilder.build()).build().writeTo(adapterOut);
            adapterOut.close();
        }
    }

    private boolean isOverlay(TypeMirror observableType) {
        // JavaScriptObject
        final TypeMirror js = processingEnv.getElementUtils().getTypeElement(JavaScriptObject.class.getName()).asType();
        // Observable<T> type
        final TypeElement ot = processingEnv.getElementUtils().getTypeElement(observableType.toString());
        return processingEnv.getTypeUtils().isSubtype(observableType, js) || ot.getAnnotation(JsType.class) != null;
    }

    private FluentIterable<AnnotationSpec> transformAnnotations(List<? extends AnnotationMirror> annotationMirrors) {
        return from(annotationMirrors)
                .filter(new Predicate<AnnotationMirror>() {
                    public boolean apply(AnnotationMirror input) {
                        return !MoreTypes.isTypeOf(RestyService.class, input.getAnnotationType());
                    }
                })
                .transform(new Function<AnnotationMirror, AnnotationSpec>() {
                    public AnnotationSpec apply(AnnotationMirror input) { return AnnotationSpec.get(input); }
                });
    }


    private void log(String msg) {
        if (processingEnv.getOptions().containsKey("debug")) {
            processingEnv.getMessager().printMessage(Kind.NOTE, msg);
        }
    }

    private void error(String msg, Element element, AnnotationMirror annotation) {
        processingEnv.getMessager().printMessage(Kind.ERROR, msg, element, annotation);
    }

    private void fatalError(String msg) {
        processingEnv.getMessager().printMessage(Kind.ERROR, "FATAL ERROR: " + msg);
    }
}
