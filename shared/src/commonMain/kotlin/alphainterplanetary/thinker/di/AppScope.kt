package alphainterplanetary.thinker.di

import me.tatarka.inject.annotations.Scope

/** Scope for app-wide singletons ([AppComponent]); each lives for the component's lifetime. */
@Scope
@Target(
  AnnotationTarget.CLASS,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.PROPERTY_GETTER,
)
annotation class AppScope