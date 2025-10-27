package org.knightmesh.core.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a class as a CK service implementation, enabling automatic discovery.
 */
@Documented
@Retention(RUNTIME)
@Target(TYPE)
public @interface CKService {
    String name();
}
