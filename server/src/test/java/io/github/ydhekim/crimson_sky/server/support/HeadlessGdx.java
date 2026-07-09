package io.github.ydhekim.crimson_sky.server.support;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;

import java.lang.reflect.Proxy;

/**
 * Server code logs through {@code com.badlogic.gdx.utils.Logger}, which delegates to {@code Gdx.app} —
 * null in a plain JUnit JVM, so any logging service under test would NPE. Production installs a real
 * {@code HeadlessApplication}; tests only need {@code Gdx.app} to be non-null and inert.
 *
 * <p>A no-op dynamic proxy is used rather than a real {@code HeadlessApplication} so no background
 * thread or application lifecycle is started just to swallow log lines.
 */
public final class HeadlessGdx {

    private HeadlessGdx() {
    }

    /** Installs a no-op {@code Gdx.app} if one isn't present. Idempotent; safe to call per test. */
    public static void install() {
        if (Gdx.app != null) {
            return;
        }
        Gdx.app = (Application) Proxy.newProxyInstance(
            HeadlessGdx.class.getClassLoader(),
            new Class<?>[]{Application.class},
            (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    /** Every call is swallowed; primitive-returning methods still need a legal value. */
    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive() || returnType == void.class) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0f;
        }
        if (returnType == double.class) {
            return 0d;
        }
        return 0;
    }
}
