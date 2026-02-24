package com.mapstash.config;

import org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlServerUntrustedCertificateSqlException;
import org.geolatte.geom.codec.PostgisWkbDecoder;
import org.geolatte.geom.crs.CrsRegistry;
import org.springframework.aot.hint.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.hibernate.spatial.HSMessageLogger;

/**
 * Registers runtime hints for GraalVM Native Image compilation.
 * <p>
 * Required for Thymeleaf templates that call methods reflectively on Java collections
 * and strings (e.g., ${list.isEmpty()}, ${string.isEmpty()}).
 * <p>
 * This is the recommended Spring Boot Native approach instead of using legacy
 * reflect-config.json files, as Spring's AOT process would overwrite custom
 * reachability-metadata.json files.
 * <p>
 * see AOT hints documentation: <a href="https://docs.spring.io/spring-native/docs/current/reference/htmlsingle/#aot-hints">...</a>
 * see <a href="https://github.com/spring-projects/spring-boot/issues/42515">...</a>
 */
@Configuration
@ImportRuntimeHints(NativeRuntimeHints.ThymeleafReflectionHints.class)
public class NativeRuntimeHints {

    static class ThymeleafReflectionHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            // Register Java collection types that Thymeleaf accesses reflectively
            hints.reflection()
                    .registerType(ArrayList.class,
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                            MemberCategory.INVOKE_DECLARED_METHODS,
                            MemberCategory.INVOKE_PUBLIC_METHODS)
                    // MemberCategory.DECLARED_FIELDS)

                    .registerType(List.class,
                            MemberCategory.INVOKE_DECLARED_METHODS,
                            MemberCategory.INVOKE_PUBLIC_METHODS)
                    .registerType(Collection.class,
                            MemberCategory.INVOKE_DECLARED_METHODS,
                            MemberCategory.INVOKE_PUBLIC_METHODS)

                    .registerType(Iterable.class,
                            MemberCategory.INVOKE_DECLARED_METHODS,
                            MemberCategory.INVOKE_PUBLIC_METHODS)

                    .registerType(String.class,
                            MemberCategory.INVOKE_DECLARED_METHODS,
                            MemberCategory.INVOKE_PUBLIC_METHODS)

                    .registerType(CharSequence.class,
                            MemberCategory.INVOKE_DECLARED_METHODS,
                            MemberCategory.INVOKE_PUBLIC_METHODS)
                    // Oops https://github.com/oracle/graalvm-reachability-metadata/issues/505
                    // Caused by: java.lang.NoSuchMethodException: org.geolatte.geom.codec.PostgisWkbDecoder.<init>()

                    .registerType(PostgisWkbDecoder.class,
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                            MemberCategory.INVOKE_DECLARED_METHODS,
                            MemberCategory.INVOKE_PUBLIC_METHODS)

                    .registerType(CrsRegistry.class,
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                            MemberCategory.INVOKE_DECLARED_METHODS,
                            MemberCategory.INVOKE_PUBLIC_METHODS)
                    // Hibernate spatial failing when running in native #233
                    // https://github.com/oracle/graalvm-reachability-metadata/issues/233
                    .registerType(HSMessageLogger.class,
                            MemberCategory.INVOKE_PUBLIC_METHODS
                    )
                    // Similar Error https://github.com/quarkusio/quarkus/issues/50106
                    .registerType(FlywaySqlServerUntrustedCertificateSqlException.class,
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                            MemberCategory.INVOKE_DECLARED_METHODS,
                            MemberCategory.INVOKE_PUBLIC_METHODS)

                    .registerType(TypeReference.of("org.hibernate.spatial.HSMessageLogger_$logger"),
                            typeHint -> typeHint.withConstructor(List.of(TypeReference.of("org.jboss.logging.Logger")), ExecutableMode.INVOKE));
        }
    }
}
