package org.girardsimon.wealthpay.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.jooq.DSLContext;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Enforces hexagonal architecture rules across all bounded contexts of the Wealthpay monolith.
 * Module-to-module dependencies are enforced separately by Spring Modulith via {@code
 * ApplicationModules.verify()} (see {@code account.ArchitectureTests}); this class enforces
 * intra-BC layering, which Modulith does not cover.
 *
 * <p>BC-specific rules ({@link #all_bounded_contexts_have_hexagonal_layers}, {@link
 * #no_bounded_context_has_utils_package}) auto-discover bounded contexts by scanning for top-level
 * packages with a {@code domain} subpackage. A new BC therefore arrives with full intra-BC layer
 * enforcement on day one — no manual rule registration. Cross-BC rules (purity, boundary, sibling
 * isolation, annotation locality, jOOQ/OpenAPI confinement) are expressed via package patterns and
 * apply automatically.
 *
 * <p>Carve-outs (each justified by a real semantic boundary):
 *
 * <ul>
 *   <li>{@code ..application.metric..} may import Micrometer's runtime API and AspectJ. That
 *       subpackage holds cross-cutting application instrumentation ({@code @CommandMetric} aspect)
 *       whose outcome lattice is application-aware. The application service itself remains
 *       framework-light beyond Spring stereotypes.
 *   <li>{@code ..infrastructure.serialization..}, {@code ..infrastructure.metric..}, {@code
 *       ..infrastructure.id..} are cross-sibling helper packages used by the I/O adapters
 *       (web/db/consumer/producer). They are not subject to I/O sibling isolation.
 *   <li>{@code ..config..} packages may reference {@link KafkaTemplate} as a parameter for bean
 *       wiring (e.g. {@code shared.config.KafkaErrorConfig} wiring {@code
 *       DeadLetterPublishingRecoverer}). Bean configuration is plumbing, not a producer adapter.
 *   <li>{@code ..jooq..} (generated jOOQ packages) and {@code ..api.generated..} (OpenAPI generated
 *       transport types) are exempt from their own confinement rules as source — they legitimately
 *       reference each other and {@code org.jooq..} types internally.
 * </ul>
 */
@AnalyzeClasses(
    packages = "org.girardsimon.wealthpay",
    importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

  private static final String BASE_PACKAGE = "org.girardsimon.wealthpay";

  @ArchTest
  static void all_bounded_contexts_have_hexagonal_layers(JavaClasses classes) {
    SortedSet<String> bcs = detectBoundedContexts(classes);
    if (bcs.isEmpty()) {
      throw new AssertionError(
          "No bounded contexts detected under "
              + BASE_PACKAGE
              + " — rule is misconfigured (expected at least one <bc>.domain subpackage).");
    }
    for (String bc : bcs) {
      hexagonalLayersFor(bc).check(classes);
    }
  }

  @ArchTest
  static final ArchRule domain_must_be_framework_free =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "org.jooq..",
              "jakarta.persistence..",
              "org.apache.kafka..",
              // Both Jackson 2.x and 3.x banned (a transitive 2.x can leak in).
              "com.fasterxml.jackson..",
              "tools.jackson..",
              "io.micrometer..",
              "jakarta.servlet..",
              "org.aspectj..",
              "com.github.f4b6a3..")
          .as("Domain must remain framework-free")
          .because(
              "Domain is the inner ring of the hexagon: pure business logic with no persistence,"
                  + " no transport, no DI framework, no observability libraries. Framework-bound"
                  + " adapters live in infrastructure; Spring-annotated implementations of"
                  + " domain-defined ports (e.g. id generators) live in infrastructure too.");

  @ArchTest
  static final ArchRule application_must_not_bind_to_infrastructure_libraries =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .and()
          .resideOutsideOfPackage("..application.metric..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.jooq..",
              "jakarta.persistence..",
              "org.apache.kafka..",
              "org.springframework.kafka..",
              "org.springframework.web..",
              "jakarta.servlet..",
              // Jackson banned (both 2.x and 3.x) — infra serialization concern.
              "com.fasterxml.jackson..",
              "tools.jackson..",
              "io.micrometer.core..",
              "org.aspectj..",
              "com.github.f4b6a3..")
          .as("Application must orchestrate via ports, not infrastructure libraries")
          .because(
              "Application use-case logic must stay framework-light. Spring stereotypes"
                  + " (@Service, @Transactional), standard Spring exceptions, and declarative"
                  + " observability annotations (@Observed from io.micrometer.observation) are"
                  + " allowed — they are markers interpreted by infrastructure handlers, not"
                  + " coupling to the meter registry. Micrometer's runtime API"
                  + " (io.micrometer.core: Timer, Counter, MeterRegistry, Sample) and AspectJ"
                  + " are confined to ..application.metric.. for command instrumentation whose"
                  + " outcome lattice is application-aware.");

  @ArchTest
  static final ArchRule web_must_not_depend_on_other_io_siblings =
      ioSiblingMustNotDependOn("web", "db", "consumer", "producer");

  @ArchTest
  static final ArchRule db_must_not_depend_on_other_io_siblings =
      ioSiblingMustNotDependOn("db", "web", "consumer", "producer");

  @ArchTest
  static final ArchRule consumer_must_not_depend_on_other_io_siblings =
      ioSiblingMustNotDependOn("consumer", "web", "db", "producer");

  /**
   * The producer slice does not exist yet. ArchUnit 1.4.x's {@code failOnEmptyShould} treats a
   * {@code that()} clause matching zero classes as a failure regardless of {@code noClasses()} vs
   * {@code classes()} — empirically verified, do not assume otherwise. The flag declares this rule
   * as preventative: it locks the convention before any drift can occur, and starts catching
   * violations the moment a producer class is added.
   */
  @ArchTest
  static final ArchRule producer_must_not_depend_on_other_io_siblings =
      ioSiblingMustNotDependOn("producer", "web", "db", "consumer").allowEmptyShould(true);

  @ArchTest
  static final ArchRule rest_controllers_only_in_web =
      classes()
          .that()
          .areMetaAnnotatedWith(RestController.class)
          .should()
          .resideInAnyPackage("..infrastructure.web..")
          .as(
              "@RestController classes (or any composed stereotype) may only live in"
                  + " ..infrastructure.web..")
          .because("HTTP transport adapters belong in the web slice");

  @ArchTest
  static final ArchRule rest_controller_advices_only_in_web =
      classes()
          .that()
          .areMetaAnnotatedWith(RestControllerAdvice.class)
          .should()
          .resideInAnyPackage("..infrastructure.web..")
          .as("@RestControllerAdvice classes may only live in ..infrastructure.web..")
          .because(
              "Exception handlers and response advisors are HTTP-transport concerns; they belong"
                  + " in the web slice next to the controllers they advise.");

  @ArchTest
  static final ArchRule kafka_listeners_only_in_consumer =
      methods()
          .that()
          .areMetaAnnotatedWith(KafkaListener.class)
          .should()
          .beDeclaredInClassesThat()
          .resideInAnyPackage("..infrastructure.consumer..")
          .as(
              "@KafkaListener methods (or any composed stereotype) may only live in"
                  + " ..infrastructure.consumer..")
          .because("Kafka consumer adapters belong in the consumer slice");

  /**
   * jOOQ — both the library ({@code org.jooq..}: {@link DSLContext}, {@code Field}, {@code JSONB},
   * etc.) and the generated package ({@code ..jooq..}: tables, records, POJOs) — is a persistence
   * concern. Anything outside {@code ..infrastructure.db..} (and the generated {@code ..jooq..}
   * itself, which legitimately imports library types) is forbidden. Closes the gap left by the
   * earlier field-only / domain-and-application-only rules: web, consumer, and producer adapters
   * could otherwise reach into jOOQ types directly and bypass the repository ports.
   */
  @ArchTest
  static final ArchRule jooq_only_used_in_db =
      noClasses()
          .that()
          .resideOutsideOfPackages("..infrastructure.db..", "..jooq..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.jooq..", "..jooq..")
          .as("jOOQ (library and generated) may only be referenced from ..infrastructure.db..")
          .because(
              "jOOQ usage must be confined to repository adapters. Application orchestrates via"
                  + " ports; controllers, consumers, producers, and cross-slice helpers must"
                  + " never reach into jOOQ directly. The layered-architecture rule does not"
                  + " catch this because generated jOOQ lives outside the three declared layers.");

  /**
   * OpenAPI-generated transport types — controller interfaces and DTO models under {@code
   * ..api.generated..} — are HTTP transport plumbing. They may only be referenced from {@code
   * ..infrastructure.web..} (controllers and web mappers, including {@code
   * shared.infrastructure.web} for the global exception handler) and from the generated package
   * itself. Domain, application, and the non-web infrastructure slices must exchange domain types,
   * not DTOs.
   */
  @ArchTest
  static final ArchRule openapi_generated_only_used_in_web =
      noClasses()
          .that()
          .resideOutsideOfPackages("..infrastructure.web..", "..api.generated..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..api.generated..")
          .as(
              "OpenAPI-generated transport types may only be referenced from ..infrastructure.web..")
          .because(
              "DTOs and generated controller interfaces are an HTTP transport concern. Domain"
                  + " and application speak in domain types; the web slice maps DTOs in and out"
                  + " via explicit mappers. Other infrastructure slices (db, consumer, producer)"
                  + " do not know HTTP exists.");

  @ArchTest
  static final ArchRule kafka_template_only_used_in_producer_or_config =
      noClasses()
          .that()
          .resideOutsideOfPackages("..infrastructure.producer..", "..config..")
          .should()
          .dependOnClassesThat()
          .areAssignableTo(KafkaTemplate.class)
          .as("KafkaTemplate may only be referenced from ..infrastructure.producer.. or ..config..")
          .because(
              "Kafka producer adapters belong in the producer slice. Bean-wiring config (e.g."
                  + " shared.config.KafkaErrorConfig) may accept KafkaTemplate as a parameter to"
                  + " wire DeadLetterPublishingRecoverer — it does not send messages itself.")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule transactional_methods_only_in_application_or_db =
      methods()
          .that()
          .areMetaAnnotatedWith(Transactional.class)
          .should()
          .beDeclaredInClassesThat()
          .resideInAnyPackage("..application..", "..infrastructure.db..")
          .as("@Transactional methods may only live in ..application.. or ..infrastructure.db..")
          .because(
              "Transactional boundaries belong with use-case orchestration (application) and the"
                  + " read-side repositories where @Transactional(readOnly=true) is appropriate."
                  + " They must not appear on controllers, consumers, producers, or domain types.")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule transactional_classes_only_in_application_or_db =
      classes()
          .that()
          .areMetaAnnotatedWith(Transactional.class)
          .should()
          .resideInAnyPackage("..application..", "..infrastructure.db..")
          .as("@Transactional classes may only live in ..application.. or ..infrastructure.db..")
          .because(
              "Class-level @Transactional applies to all public methods of the bean — same"
                  + " constraints as method-level.")
          .allowEmptyShould(true);

  /**
   * Prevents domain value objects from offering a {@code newId()} static factory. ID minting must
   * go through a domain-defined generator port (e.g. {@code AccountIdGenerator}) so the
   * randomness/UUID-version policy lives in exactly one place — the production adapter — and tests
   * exercise the same seam (test generators implementing the same port) instead of bypassing it.
   * The rule is type-agnostic: any future {@code FooId} value object inherits the constraint
   * automatically. It also catches a non-ID class in {@code ..domain.model..} that grows a static
   * {@code newId()} — intentional, since domain-model classes should not self-mint identifiers.
   */
  @ArchTest
  static final ArchRule domain_model_must_not_expose_newId_static_factory =
      noMethods()
          .that()
          .areStatic()
          .and()
          .haveName("newId")
          .should()
          .beDeclaredInClassesThat()
          .resideInAPackage("..domain.model..")
          .as("Domain value objects must not expose a static newId() factory")
          .because(
              "ID minting must go through a domain-defined generator port (e.g."
                  + " AccountIdGenerator) so the policy (UUIDv4 vs UUIDv7, random source) lives in"
                  + " one place — the production adapter — and tests use test generators"
                  + " implementing the same port instead of bypassing it. The same constraint"
                  + " applies to any other domain-model class: they should not self-mint"
                  + " identifiers.")
          .allowEmptyShould(true);

  @ArchTest
  static void no_bounded_context_has_utils_package(JavaClasses classes) {
    for (String bc : detectBoundedContexts(classes)) {
      noClasses()
          .should()
          .resideInAnyPackage("..%s.utils..".formatted(bc))
          .as("BC '%s' must not have a 'utils' package at its root".formatted(bc))
          .because(
              "A 'utils' package at BC root becomes a cross-layer dependency hub that hides"
                  + " framework leaks and silently violates layering. Place helpers in their"
                  + " actual layer — e.g. infrastructure.serialization for JSON helpers shared"
                  + " across infrastructure I/O slices. The shared open module (shared.utils)"
                  + " is exempt by definition since it holds cross-BC helpers and is not a BC.")
          .allowEmptyShould(true)
          .check(classes);
    }
  }

  /**
   * {@code consideringOnlyDependenciesInLayers()} means dependencies on classes outside the three
   * declared layers (Java stdlib, Spring, jOOQ-generated, OpenAPI-generated, etc.) are ignored by
   * THIS rule — they are governed by the targeted purity/boundary/confinement rules above. Without
   * it, every Spring/jOOQ/Jackson type would need explicit declaration here, which would either
   * pollute the rule or silently force the wrong allow-list. The non-layer escape hatches ({@code
   * ..jooq..}, {@code ..api.generated..}) are handled by {@link #jooq_only_used_in_db} and {@link
   * #openapi_generated_only_used_in_web}.
   */
  private static ArchRule hexagonalLayersFor(String boundedContext) {
    return Architectures.layeredArchitecture()
        .consideringOnlyDependenciesInLayers()
        .layer("Domain")
        .definedBy("..%s.domain..".formatted(boundedContext))
        .layer("Application")
        .definedBy("..%s.application..".formatted(boundedContext))
        .layer("Infrastructure")
        .definedBy("..%s.infrastructure..".formatted(boundedContext))
        .whereLayer("Infrastructure")
        .mayNotBeAccessedByAnyLayer()
        .whereLayer("Application")
        .mayOnlyBeAccessedByLayers("Infrastructure")
        .whereLayer("Domain")
        .mayOnlyBeAccessedByLayers("Application", "Infrastructure")
        .as("Hexagonal layering for %s".formatted(boundedContext))
        .because(
            "Inner layers (domain) must not depend on outer layers (application, infrastructure)."
                + " Application depends only on domain. Infrastructure depends on application and"
                + " domain via ports.");
  }

  private static ArchRule ioSiblingMustNotDependOn(String mySlice, String... forbiddenSiblings) {
    String[] forbiddenPackages =
        Arrays.stream(forbiddenSiblings)
            .map("..infrastructure.%s.."::formatted)
            .toArray(String[]::new);
    return noClasses()
        .that()
        .resideInAPackage("..infrastructure." + mySlice + "..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(forbiddenPackages)
        .as(
            "Infrastructure.%s must not depend on sibling I/O slices: %s"
                .formatted(mySlice, String.join(", ", forbiddenSiblings)))
        .because(
            "Infrastructure I/O adapters communicate via application ports, not directly with"
                + " each other. Cross-slice helpers live in ..infrastructure.serialization..,"
                + " ..infrastructure.metric.., ..infrastructure.id..");
  }

  /**
   * Discovers bounded contexts by scanning imported classes for top-level segments that have a
   * {@code domain} subpackage. {@code shared} is naturally excluded (no {@code shared.domain}), as
   * is {@code WealthpayApplication} (top-level class with no sub-segments). Tests are excluded via
   * {@link ImportOption.DoNotIncludeTests} on the class-level {@code @AnalyzeClasses}.
   */
  private static SortedSet<String> detectBoundedContexts(JavaClasses classes) {
    Map<String, Set<String>> bcLayers = new HashMap<>();
    for (var javaClass : classes) {
      String pkg = javaClass.getPackageName();
      if (!pkg.startsWith(BASE_PACKAGE + ".")) {
        continue;
      }
      String tail = pkg.substring(BASE_PACKAGE.length() + 1);
      String[] parts = tail.split("\\.", 3);
      if (parts.length < 2) {
        continue;
      }
      String bc = parts[0];
      String layer = parts[1];
      if (layer.equals("domain") || layer.equals("application") || layer.equals("infrastructure")) {
        bcLayers.computeIfAbsent(bc, ignored -> new HashSet<>()).add(layer);
      }
    }
    SortedSet<String> result = new TreeSet<>();
    for (Map.Entry<String, Set<String>> entry : bcLayers.entrySet()) {
      // A BC must have at least a domain subpackage (the defining trait of hexagonal architecture).
      if (entry.getValue().contains("domain")) {
        result.add(entry.getKey());
      }
    }
    return result;
  }
}
