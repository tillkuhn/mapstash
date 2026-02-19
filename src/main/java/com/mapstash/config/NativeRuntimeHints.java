package com.mapstash.config;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Registers runtime hints for GraalVM Native Image compilation.
 *
 * Required for Thymeleaf templates that call methods reflectively on Java collections
 * and strings (e.g., ${list.isEmpty()}, ${string.isEmpty()}).
 *
 * This is the recommended Spring Boot Native approach instead of using legacy
 * reflect-config.json files, as Spring's AOT process would overwrite custom
 * reachability-metadata.json files.
 *
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
                    MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.DECLARED_FIELDS)
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
                    MemberCategory.INVOKE_PUBLIC_METHODS);
        }
    }
}
