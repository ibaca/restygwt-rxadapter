package com.intendia.gwt.restyrx.processor;

import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.auto.common.MoreTypes.isTypeOf;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.FluentIterable.from;
import static java.util.Collections.singleton;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.auto.common.MoreElements;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.shared.GWT;
import com.google.gwt.http.client.Request;
import com.intendia.gwt.restyrx.client.AbstractAdapterService;
import com.intendia.gwt.restyrx.client.RestyService;
import com.intendia.gwt.restyrx.client.RestyService.TypeMap;
import com.intendia.gwt.restyrx.client.SubscriberMethodCallback;
import com.intendia.gwt.restyrx.client.SubscriberMethodCallback.JsList;
import com.intendia.gwt.restyrx.client.SubscriberMethodCallback.RequestObservableOnSubscribe;
import com.intendia.gwt.restyrx.client.SubscriberMethodCallback.RequestSingleOnSubscribe;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;
import jsinterop.annotations.JsType;
import org.fusesource.restygwt.client.MethodCallback;
import org.fusesource.restygwt.client.OverlayCallback;
import org.fusesource.restygwt.client.RestService;
import rx.Observable;
import rx.Single;
import rx.SingleSubscriber;
import rx.Subscriber;

public class RestyServiceAdapterProcessor extends AbstractProcessor {

    private Set<? extends TypeElement> annotations;
    private RoundEnvironment roundEnv;

    @Override public Set<String> getSupportedAnnotationTypes() { return singleton(RestyService.class.getName()); }

    @Override public SourceVersion getSupportedSourceVersion() { return SourceVersion.latestSupported(); }

    @Override public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return false;

        this.annotations = annotations;
        this.roundEnv = roundEnv;
        try {
            processAnnotations();
        } catch (Exception e) {
            // We don't allow exceptions of any kind to propagate to the compiler
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            fatalError(writer.toString());
        }
        return true;
    }

    private void processAnnotations() throws Exception {
        List<? extends TypeElement> elements = FluentIterable.from(roundEnv.getElementsAnnotatedWith(RestyService.class))
                .filter(TypeElement.class)
                .filter(input -> input.getKind().isInterface())
                .toList();

        log(annotations.toString());
        log(elements.toString());

        Map<TypeMirror, TypeMirror> typeMap = new HashMap<>();

        for (TypeElement restService : elements) {
            AnnotationMirror annotation = MoreElements.getAnnotationMirror(restService, RestyService.class).get();

            TypeMap[] types = restService.getAnnotation(RestyService.class).types();
            for (TypeMap type : types) typeMap.put(typeMap_type(type), typeMap_with(type));
            Function<TypeMirror, TypeMirror> typeMapper = t -> typeMap.getOrDefault(t, t);

            ClassName serviceName = ClassName.get(restService);
            log("service interface: " + serviceName);

            ClassName restyName = ClassName.get(serviceName.packageName(), serviceName.simpleName() + "_RestyService");
            log("service resty interface: " + restyName);

            ClassName adapterName = ClassName
                    .get(serviceName.packageName(), serviceName.simpleName() + "_RestyAdapter");
            log("service resty adapter: " + adapterName);

            TypeSpec.Builder restyBuilder = TypeSpec.interfaceBuilder(restyName.simpleName())
                    .addAnnotations(transformAnnotations(restService.getAnnotationMirrors()))
                    .addOriginatingElement(restService)
                    .addModifiers(Modifier.PUBLIC)
                    .addSuperinterface(RestService.class);

            TypeSpec.Builder adapterBuilder = TypeSpec.classBuilder(adapterName.simpleName())
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

            FluentIterable<? extends ExecutableElement> methods = from(restService.getEnclosedElements())
                    .filter(element -> element.getKind() == ElementKind.METHOD)
                    .filter(ExecutableElement.class)
                    .filter(method -> !(method.getModifiers().contains(STATIC) || method.isDefault()));
            for (ExecutableElement method : methods) {
                String methodName = method.getSimpleName().toString();

                if (isIncompatible(method)) {
                    adapterBuilder.addMethod(MethodSpec.overriding(method)
                            .addStatement("throw new $T(\"$L\")", UnsupportedOperationException.class, methodName)
                            .build());
                    continue;
                }

                boolean isObservable = isTypeOf(Observable.class, method.getReturnType());
                boolean isSingle = isTypeOf(Single.class, method.getReturnType());
                if (!isObservable && !isSingle) {
                    error("Observable<T> return type required", method, annotation);
                    continue;
                }

                TypeMirror returnType = asDeclared(method.getReturnType()).getTypeArguments().get(0);
                // XXX restygwt is type bounds hostile, implements bounds and wildcards is not trivial (disabled)
                // if (returnType instanceof TypeVariable) {
                //     returnType = ((TypeVariable) returnType).getUpperBound();
                // } else if (returnType instanceof WildcardType) {
                //     returnType = processingEnv.getTypeUtils().erasure(returnType);
                // }
                TypeMirror serviceReturnT = typeMapper.apply(returnType);
                TypeName serviceReturnN = TypeName.get(serviceReturnT);

                CodeBlock.Builder paramCasts = CodeBlock.builder();
                String parameterNames = from(method.getParameters())
                        .transform(parameter -> {
                            String paramName = parameter.getSimpleName().toString();
                            if (typeMap.containsKey(parameter.asType())) {
                                // requires casting
                                paramName = "$" + paramName;
                                paramCasts.addStatement("final $T $N = ($1T) $N",
                                        typeMapper.apply(parameter.asType()), paramName, parameter.getSimpleName());
                            }
                            return paramName;
                        })
                        .join(Joiner.on(", "));

                // Resty methods
                TypeName javaWrap = isObservable
                        ? ParameterizedTypeName.get(ClassName.get(List.class), serviceReturnN)
                        : serviceReturnN;
                TypeName scriptWrap = isObservable
                        ? ParameterizedTypeName.get(ClassName.get(JsList.class), serviceReturnN)
                        : isTypeOf(Void.class, serviceReturnT) ? TypeName.get(JavaScriptObject.class) : serviceReturnN;
                restyBuilder.addMethod(MethodSpec
                        .methodBuilder(methodName)
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .addAnnotations(transformAnnotations(method.getAnnotationMirrors()))
                        .returns(Request.class)
                        .addParameters(from(method.getParameters())
                                .transform(p -> ParameterSpec.builder(
                                        TypeName.get(typeMapper.apply(p.asType())), p.getSimpleName().toString())
                                        .addAnnotations(transformAnnotations(p.getAnnotationMirrors()))
                                        .build()))
                        .addParameter(isOverlay(serviceReturnT)
                                ? ParameterizedTypeName.get(ClassName.get(OverlayCallback.class), scriptWrap)
                                : ParameterizedTypeName.get(ClassName.get(MethodCallback.class), javaWrap), "callback")
                        .build());

                // Adapter methods
                adapterBuilder.addMethod(MethodSpec
                        .methodBuilder(methodName)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(TypeName.get(method.getReturnType()))
                        .addParameters(from(method.getParameters())
                                .transform(p -> ParameterSpec
                                        .builder(TypeName.get(p.asType()), p.getSimpleName().toString())
                                        .addModifiers(Modifier.FINAL)
                                        .addAnnotations(transformAnnotations(p.getAnnotationMirrors()))
                                        .build()))
                        .addCode("return $1T.create(new $2T<$3T>() {\n$>",
                                /*1*/ ClassName.get(isObservable ? Observable.class : Single.class),
                                /*2*/ ClassName.get(isObservable
                                        ? RequestObservableOnSubscribe.class : RequestSingleOnSubscribe.class),
                                /*3*/ ClassName.get(returnType))
                        .addCode(paramCasts.build())
                        .addCode("public $1T request($2T<? super $3T> $$S) {\n" +
                                        "  return service().$4L($5L$6T.<$7T>$8L($$S));\n" +
                                        "};\n",
                                /*1*/ Request.class,
                                /*2*/ ClassName.get(isObservable ? Subscriber.class : SingleSubscriber.class),
                                /*3*/ ClassName.get(returnType),
                                /*4*/ methodName,
                                /*5*/ isNullOrEmpty(parameterNames) ? "" : parameterNames + ", ",
                                /*6*/ ClassName.get(SubscriberMethodCallback.class),
                                /*7*/ serviceReturnT,
                                /*8*/ isOverlay(serviceReturnT) ? "overlay" : "pojo")
                        .addCode("$<});\n")
                        .build());
            }

            Filer filer = processingEnv.getFiler();
            JavaFile.builder(serviceName.packageName(), restyBuilder.build()).build().writeTo(filer);
            JavaFile.builder(serviceName.packageName(), adapterBuilder.build()).build().writeTo(filer);
        }
    }

    private boolean isIncompatible(ExecutableElement method) {
        return method.getAnnotationMirrors().stream().anyMatch(this::isIncompatible);
    }

    private boolean isIncompatible(AnnotationMirror a) {
        return a.getAnnotationType().toString().endsWith("GwtIncompatible");
    }

    private boolean isOverlay(TypeMirror T) {
        // Void
        TypeMirror vd = processingEnv.getElementUtils().getTypeElement(Void.class.getName()).asType();
        // JavaScriptObject
        TypeMirror js = processingEnv.getElementUtils().getTypeElement(JavaScriptObject.class.getName()).asType();
        return processingEnv.getTypeUtils().isSubtype(T, js)
                || T.getAnnotationsByType(JsType.class).length > 0
                || processingEnv.getTypeUtils().isSameType(T, vd);
    }

    private Iterable<AnnotationSpec> transformAnnotations(List<? extends AnnotationMirror> annotationMirrors) {
        return from(annotationMirrors)
                .filter(input -> !isTypeOf(RestyService.class, input.getAnnotationType()))
                .transform(AnnotationSpec::get);
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

    private TypeMirror typeMap_type(TypeMap annotation) {
        try {
            annotation.type();
            throw new RuntimeException("unreachable");
        } catch (MirroredTypeException exception) {
            return exception.getTypeMirror();
        }
    }

    private TypeMirror typeMap_with(TypeMap annotation) {
        try {
            annotation.with();
            throw new RuntimeException("unreachable");
        } catch (MirroredTypeException exception) {
            return exception.getTypeMirror();
        }
    }
}
