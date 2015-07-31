package org.fusesource.restygwt.processor;

import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.FluentIterable.from;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.service.AutoService;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.js.JsType;
import com.google.gwt.core.shared.GWT;
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
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Processor;
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
import org.fusesource.restygwt.client.AbstractAdapterService;
import org.fusesource.restygwt.client.MethodCallback;
import org.fusesource.restygwt.client.OverlayCallback;
import org.fusesource.restygwt.client.RestService;
import org.fusesource.restygwt.client.RestyService;
import org.fusesource.restygwt.client.RestyService.TypeMap;
import org.fusesource.restygwt.client.SubscriberMethodCallback;
import org.fusesource.restygwt.client.SubscriberMethodCallback.JsList;
import rx.Observable;
import rx.Subscriber;

@AutoService(Processor.class)
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

    private void processAnnotations(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
            throws Exception {
        Filer filer = processingEnv.getFiler();

        List<? extends TypeElement> elements = from(roundEnv.getElementsAnnotatedWith(RestyService.class))
                .filter(TypeElement.class)
                .filter(input -> input.getKind().isInterface())
                .toList();

        log(annotations.toString());
        log(elements.toString());

        final Map<TypeMirror, TypeMirror> typeMap = new HashMap<>();

        for (TypeElement restService : elements) {
            final AnnotationMirror annotation = MoreElements.getAnnotationMirror(restService, RestyService.class).get();

            final TypeMap[] types = restService.getAnnotation(RestyService.class).types();
            for (TypeMap type : types) typeMap.put(typeMap_type(type), typeMap_with(type));
            final Function<TypeMirror, TypeMirror> typeMapper = t -> typeMap.getOrDefault(t, t);

            ClassName serviceName = ClassName.get(restService);
            log("service interface: " + serviceName);

            ClassName restyName = ClassName.get(serviceName.packageName(), serviceName.simpleName() + "_RestyService");
            log("service resty interface: " + restyName);

            ClassName adapterName = ClassName
                    .get(serviceName.packageName(), serviceName.simpleName() + "_RestyAdapter");
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
                    .filter(input -> input.getKind() == ElementKind.METHOD)
                    .filter(ExecutableElement.class);
            for (ExecutableElement method : methods) {
                final String methodName = method.getSimpleName().toString();

                if (isIncompatible(method)) {
                    adapterBuilder.addMethod(MethodSpec.overriding(method)
                            .addStatement("throw new $T(\"$L\")", UnsupportedOperationException.class, methodName)
                            .build());
                    continue;
                }

                if (!MoreTypes.isTypeOf(Observable.class, method.getReturnType())) {
                    error("Observable<T> return type required", method, annotation);
                    continue;
                }

                final TypeMirror returnType = asDeclared(method.getReturnType()).getTypeArguments().get(0);
                final TypeMirror serviceType = typeMapper.apply(returnType);
                final CodeBlock.Builder paramCasts = CodeBlock.builder();
                final String parameterNames = from(method.getParameters())
                        .transform(parameter -> {
                            String paramName = parameter.getSimpleName().toString();
                            if (typeMap.containsKey(parameter.asType())) {
                                // requires casting
                                paramName = paramName + "Cast";
                                paramCasts.addStatement("$T $N = ($1T) $N",
                                        typeMapper.apply(parameter.asType()), paramName, parameter.getSimpleName());
                            }
                            return paramName;
                        })
                        .join(Joiner.on(", "));

                // Resty methods
                TypeName javaWrap = ParameterizedTypeName.get(ClassName.get(List.class), TypeName.get(serviceType));
                TypeName scriptWrap = ParameterizedTypeName.get(ClassName.get(JsList.class), TypeName.get(serviceType));
                restyBuilder.addMethod(MethodSpec
                        .methodBuilder(methodName)
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .addAnnotations(transformAnnotations(method.getAnnotationMirrors()))
                        .addParameters(from(method.getParameters())
                                .transform(p -> ParameterSpec.builder(
                                        TypeName.get(typeMapper.apply(p.asType())), p.getSimpleName().toString())
                                        .addAnnotations(transformAnnotations(p.getAnnotationMirrors()))
                                        .build()))
                        .addParameter(isOverlay(serviceType)
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
                        .addCode(paramCasts.build())
                        .addStatement("return $1T.create(new $2T<$3T>() {\n" +
                                        "  public void call($4T<? super $3T> subscription) {\n" +
                                        "    service().$5L($6L$7T.$8L(subscription));\n" +
                                        "  }\n" +
                                        "})",
                                ClassName.get(Observable.class),
                                ClassName.get(Observable.OnSubscribe.class),
                                ClassName.get(returnType),
                                ClassName.get(Subscriber.class),
                                methodName,
                                isNullOrEmpty(parameterNames) ? "" : parameterNames + ", ",
                                ClassName.get(SubscriberMethodCallback.class),
                                isOverlay(serviceType) ? "overlay" : "pojo"
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

    private boolean isIncompatible(ExecutableElement method) {
        for (AnnotationMirror annotationMirror : method.getAnnotationMirrors()) {
            if (annotationMirror.getAnnotationType().toString().endsWith("GwtIncompatible")) {
                return true;
            }
        }
        return false;
    }

    private boolean isOverlay(TypeMirror T) {
        // Void
        final TypeMirror vd = processingEnv.getElementUtils().getTypeElement(Void.class.getName()).asType();
        // JavaScriptObject
        final TypeMirror js = processingEnv.getElementUtils().getTypeElement(JavaScriptObject.class.getName()).asType();
        // Observable<T> type
        final TypeElement ot = processingEnv.getElementUtils().getTypeElement(T.toString());
        return processingEnv.getTypeUtils().isSubtype(T, js)
                || ot.getAnnotation(JsType.class) != null
                || processingEnv.getTypeUtils().isSameType(T, vd);
    }

    private FluentIterable<AnnotationSpec> transformAnnotations(List<? extends AnnotationMirror> annotationMirrors) {
        return from(annotationMirrors)
                .filter(input -> !MoreTypes.isTypeOf(RestyService.class, input.getAnnotationType()))
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
