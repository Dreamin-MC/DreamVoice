package fr.dreamin.dreamvoice.api.filter.annotation;

import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation marking a {@link VoiceFilter}
 * class for automatic discovery and registration by {@link VoiceFilterService}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoVoiceFilter {

  /**
   * Execution priority when chained (higher priority runs first).
   * Overrides {@link VoiceFilter#getPriority()} if specified.
   *
   * @return priority level
   */
  int priority() default 0;

  /**
   * Whether this filter is enabled by default upon automatic registration.
   *
   * @return true if enabled
   */
  boolean enabled() default true;

}
