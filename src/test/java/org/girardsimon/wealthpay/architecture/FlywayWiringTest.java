package org.girardsimon.wealthpay.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

/**
 * Guards the failure modes the per-BC Flyway design cannot make structurally impossible: a bounded
 * context whose migrations exist but whose beans never run them, an initializer that migrates the
 * wrong BC's schema, and a configuration that ignores {@code spring.flyway.enabled}. All of them
 * are silent in production — a missing configuration class leaves the directory inert, a missing
 * {@code FlywayMigrationInitializer} leaves a {@code Flyway} bean that no one asks to migrate (and
 * which has already backed off the auto-configuration that would have), a mis-qualified initializer
 * migrates a schema twice while leaving another uncreated, and a missing condition turns the kill
 * switch into a no-op.
 *
 * <p>Every assertion is driven from a classpath scan rather than a list of bounded contexts, so
 * adding one is covered on the day it lands.
 */
class FlywayWiringTest {

  private static final String BASE_PACKAGE = "org.girardsimon.wealthpay";
  private static final String MIGRATION_ROOT = "db/migration";

  @Test
  void every_migration_directory_is_migrated_by_a_declared_flyway_bean() {
    // Arrange
    SortedSet<String> migrationDirectories = migrationDirectoryNames();

    // Act
    SortedSet<String> migratedSchemas = schemasOfDeclaredFlywayBeans();

    // Assert
    assertThat(migrationDirectories).isNotEmpty();
    assertThat(migratedSchemas)
        .as("each db/migration/<schema> directory needs a Flyway bean defaulting to that schema")
        .isEqualTo(migrationDirectories);
  }

  @Test
  void every_flyway_bean_is_migrated_by_an_initializer_qualified_to_it() {
    // Arrange
    List<Class<?>> configurations = flywayConfigurationClasses();

    // Act / Assert
    assertThat(configurations).isNotEmpty();
    assertThat(configurations).allSatisfy(FlywayWiringTest::assertInitializerMigratesItsOwnBean);
  }

  /**
   * {@code spring.flyway.enabled} is the reason each configuration carries a condition at all:
   * Spring Boot's own copy of the key gates only the autoconfiguration, which our beans have
   * already backed off, so without the condition they would migrate regardless and the switch would
   * be a lie — worse than absent, because it also drops the {@code
   * DatabaseInitializationDependencyConfigurer} import that orders DataSource consumers after
   * migration.
   *
   * <p>Asserted per discovered configuration rather than once per bounded context, so a new BC is
   * covered the day it is added instead of when someone remembers to write its test.
   */
  @Test
  void every_flyway_configuration_is_removed_by_the_kill_switch() {
    // Arrange
    List<Class<?>> configurations = flywayConfigurationClasses();

    // Act / Assert
    assertThat(configurations).isNotEmpty();
    assertThat(configurations).allSatisfy(FlywayWiringTest::assertKillSwitchRemovesBothBeans);
  }

  private static void assertKillSwitchRemovesBothBeans(Class<?> configuration) {
    runnerFor(configuration)
        .run(
            context ->
                assertThat(context)
                    .as("%s must contribute both beans by default", configuration.getSimpleName())
                    .hasSingleBean(Flyway.class)
                    .hasSingleBean(FlywayMigrationInitializer.class));

    runnerFor(configuration)
        .withPropertyValues("spring.flyway.enabled=false")
        .run(
            context ->
                assertThat(context)
                    .as(
                        "spring.flyway.enabled=false must remove %s's beans, not only the"
                            + " autoconfiguration they replaced",
                        configuration.getSimpleName())
                    .doesNotHaveBean(Flyway.class)
                    .doesNotHaveBean(FlywayMigrationInitializer.class));
  }

  /**
   * Lazy initialization keeps the assertions at the bean-definition level: instantiating {@code
   * FlywayMigrationInitializer} would run {@code migrate()} against the mock DataSource.
   */
  private static ApplicationContextRunner runnerFor(Class<?> configuration) {
    return new ApplicationContextRunner()
        .withInitializer(
            context ->
                context.addBeanFactoryPostProcessor(
                    new LazyInitializationBeanFactoryPostProcessor()))
        .withBean(DataSource.class, () -> mock(DataSource.class))
        .withUserConfiguration(configuration);
  }

  /**
   * The qualifier is the half that a copy-pasted configuration gets wrong: an initializer pointing
   * at another BC's {@code Flyway} bean migrates that schema twice — idempotently, so silently —
   * and never creates its own.
   */
  private static void assertInitializerMigratesItsOwnBean(Class<?> configuration) {
    Method flywayBean = soleBeanMethodReturning(configuration, Flyway.class).orElseThrow();
    Optional<Method> initializer =
        soleBeanMethodReturning(configuration, FlywayMigrationInitializer.class);

    assertThat(initializer)
        .as(
            "%s declares a Flyway bean but nothing that calls migrate()",
            configuration.getSimpleName())
        .isPresent();
    assertThat(qualifierOfFlywayParameter(initializer.orElseThrow()))
        .as(
            "%s's initializer must @Qualifier the Flyway bean it migrates; without it, by-type"
                + " injection is ambiguous the moment a second BC exists",
            configuration.getSimpleName())
        .contains(beanNameOf(flywayBean));
  }

  private static Optional<String> qualifierOfFlywayParameter(Method initializer) {
    return Arrays.stream(initializer.getParameters())
        .filter(parameter -> Flyway.class.isAssignableFrom(parameter.getType()))
        .map(parameter -> parameter.getAnnotation(Qualifier.class))
        .filter(Objects::nonNull)
        .map(Qualifier::value)
        .findFirst();
  }

  private static String beanNameOf(Method beanMethod) {
    String[] declaredNames = beanMethod.getAnnotation(Bean.class).name();
    return declaredNames.length == 0 ? beanMethod.getName() : declaredNames[0];
  }

  /** Scans every classpath root: test resources shadow main resources on the first hit only. */
  private static SortedSet<String> migrationDirectoryNames() {
    SortedSet<String> names = new TreeSet<>();
    try {
      Enumeration<URL> roots = FlywayWiringTest.class.getClassLoader().getResources(MIGRATION_ROOT);
      while (roots.hasMoreElements()) {
        Path root = Path.of(roots.nextElement().toURI());
        try (Stream<Path> entries = Files.list(root)) {
          entries.filter(Files::isDirectory).map(FlywayWiringTest::fileName).forEach(names::add);
        }
      }
    } catch (IOException | URISyntaxException e) {
      throw new AssertionError("could not list " + MIGRATION_ROOT, e);
    }
    return names;
  }

  private static String fileName(Path path) {
    return path.getFileName().toString();
  }

  private static SortedSet<String> schemasOfDeclaredFlywayBeans() {
    SortedSet<String> schemas = new TreeSet<>();
    for (Class<?> configuration : flywayConfigurationClasses()) {
      schemas.add(buildFlywayBean(configuration).getConfiguration().getDefaultSchema());
    }
    return schemas;
  }

  private static List<Class<?>> flywayConfigurationClasses() {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Configuration.class));
    return scanner.findCandidateComponents(BASE_PACKAGE).stream()
        .<Class<?>>map(FlywayWiringTest::resolveClass)
        .filter(configuration -> !beanMethodsReturning(configuration, Flyway.class).isEmpty())
        .toList();
  }

  private static Class<?> resolveClass(BeanDefinition definition) {
    return ClassUtils.resolveClassName(Objects.requireNonNull(definition.getBeanClassName()), null);
  }

  private static Flyway buildFlywayBean(Class<?> configuration) {
    Method beanMethod = soleBeanMethodReturning(configuration, Flyway.class).orElseThrow();
    try {
      Object instance = configuration.getDeclaredConstructor().newInstance();
      Object[] arguments =
          Arrays.stream(beanMethod.getParameterTypes()).map(FlywayWiringTest::stub).toArray();
      return (Flyway) beanMethod.invoke(instance, arguments);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("could not build the Flyway bean declared by " + configuration, e);
    }
  }

  private static Object stub(Class<?> parameterType) {
    if (parameterType == DataSource.class) {
      return mock(DataSource.class);
    }
    if (parameterType == ResourceLoader.class) {
      return new DefaultResourceLoader();
    }
    throw new AssertionError(
        "a Flyway @Bean method takes a %s, which this test cannot supply — extend stub() rather than"
                .formatted(parameterType.getName())
            + " dropping the assertion");
  }

  /**
   * {@code getDeclaredMethods()} has no specified order, so a second match would be a coin toss.
   */
  private static Optional<Method> soleBeanMethodReturning(
      Class<?> configuration, Class<?> returnType) {
    List<Method> beanMethods = beanMethodsReturning(configuration, returnType);
    assertThat(beanMethods)
        .as(
            "%s must declare at most one @Bean returning %s",
            configuration.getSimpleName(), returnType.getSimpleName())
        .hasSizeLessThanOrEqualTo(1);
    return beanMethods.stream().findFirst();
  }

  private static List<Method> beanMethodsReturning(Class<?> configuration, Class<?> returnType) {
    return Arrays.stream(configuration.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(Bean.class))
        .filter(method -> returnType.isAssignableFrom(method.getReturnType()))
        .toList();
  }
}
