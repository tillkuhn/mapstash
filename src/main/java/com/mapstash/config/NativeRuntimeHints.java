package com.mapstash.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlNoIntegratedAuthException;
import org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlServerUntrustedCertificateSqlException;
import org.geolatte.geom.codec.PostgisWkbDecoder;
import org.geolatte.geom.codec.PostgisWkbV2Encoder;
import org.geolatte.geom.crs.CrsRegistry;
import org.hibernate.spatial.HSMessageLogger;
import org.springframework.aot.hint.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * Registers runtime hints for GraalVM Native Image compilation.
 *
 * <p>Required for Thymeleaf templates that call methods reflectively on Java collections and
 * strings (e.g., ${list.isEmpty()}, ${string.isEmpty()}).
 *
 * <p>This is the recommended Spring Boot Native approach instead of using legacy
 * reflect-config.json files, as Spring's AOT process would overwrite custom
 * reachability-metadata.json files.
 *
 * <p>see AOT hints documentation: <a
 * href="https://docs.spring.io/spring-native/docs/current/reference/htmlsingle/#aot-hints">...</a>
 * see <a href="https://github.com/spring-projects/spring-boot/issues/42515">...</a>
 */
@Configuration
@ImportRuntimeHints(NativeRuntimeHints.AppReflectionHints.class)
public class NativeRuntimeHints {

  static class AppReflectionHints implements RuntimeHintsRegistrar {

    // Reusable category groups to avoid repeating the same MemberCategory values
    private static final MemberCategory[] CONSTRUCTORS_AND_METHODS = {
      MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
      MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
      MemberCategory.INVOKE_DECLARED_METHODS,
      MemberCategory.INVOKE_PUBLIC_METHODS
    };

    private static final MemberCategory[] METHODS_ONLY = {
      MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_PUBLIC_METHODS
    };

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
      // Use a local reference to the reflection hints and reuse the category arrays
      var reflection = hints.reflection();

      // Register Java collection types that Thymeleaf accesses reflectively
      reflection.registerType(ArrayList.class, CONSTRUCTORS_AND_METHODS);
      reflection.registerType(List.class, METHODS_ONLY);
      reflection.registerType(Collection.class, METHODS_ONLY);
      reflection.registerType(Iterable.class, METHODS_ONLY);
      reflection.registerType(String.class, METHODS_ONLY);
      reflection.registerType(CharSequence.class, METHODS_ONLY);
      // Oops https://github.com/oracle/graalvm-reachability-metadata/issues/505
      // Caused by: java.lang.NoSuchMethodException:
      // org.geolatte.geom.codec.PostgisWkbDecoder.<init>()
      reflection.registerType(PostgisWkbDecoder.class, CONSTRUCTORS_AND_METHODS);
      reflection.registerType(PostgisWkbV2Encoder.class, CONSTRUCTORS_AND_METHODS);
      reflection.registerType(CrsRegistry.class, CONSTRUCTORS_AND_METHODS);
      // Hibernate spatial failing when running in native #233
      // https://github.com/oracle/graalvm-reachability-metadata/issues/233
      reflection.registerType(HSMessageLogger.class, MemberCategory.INVOKE_PUBLIC_METHODS);
      // Similar Error https://github.com/quarkusio/quarkus/issues/50106
      reflection.registerType(
          FlywaySqlServerUntrustedCertificateSqlException.class, CONSTRUCTORS_AND_METHODS);
      reflection.registerType(
              FlywaySqlNoIntegratedAuthException.class, CONSTRUCTORS_AND_METHODS);
      reflection.registerType(
          TypeReference.of("org.hibernate.spatial.HSMessageLogger_$logger"),
          typeHint ->
              typeHint.withConstructor(
                  List.of(TypeReference.of("org.jboss.logging.Logger")), ExecutableMode.INVOKE));
    }
  }
}
