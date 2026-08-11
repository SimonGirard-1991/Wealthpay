package org.girardsimon.wealthpay.customer.infrastructure.db.repository;

import static org.girardsimon.wealthpay.customer.jooq.tables.CustomerAdmission.CUSTOMER_ADMISSION;
import static org.girardsimon.wealthpay.customer.jooq.tables.CustomerNationality.CUSTOMER_NATIONALITY;
import static org.girardsimon.wealthpay.customer.jooq.tables.CustomerStatusTransition.CUSTOMER_STATUS_TRANSITION;
import static org.jooq.impl.DSL.arrayAgg;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectOne;
import static org.jooq.impl.DSL.val;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.girardsimon.wealthpay.customer.application.Actor;
import org.girardsimon.wealthpay.customer.application.AdmissionDecisionId;
import org.girardsimon.wealthpay.customer.application.CustomerNumberCollisionException;
import org.girardsimon.wealthpay.customer.application.CustomerStore;
import org.girardsimon.wealthpay.customer.application.EmailAlreadyRegisteredException;
import org.girardsimon.wealthpay.customer.application.LoadedCustomer;
import org.girardsimon.wealthpay.customer.application.StatusTransition;
import org.girardsimon.wealthpay.customer.application.TransitionOutcome;
import org.girardsimon.wealthpay.customer.domain.exception.CustomerRowCorruptException;
import org.girardsimon.wealthpay.customer.domain.model.Customer;
import org.girardsimon.wealthpay.customer.domain.model.CustomerId;
import org.girardsimon.wealthpay.customer.domain.model.CustomerState;
import org.girardsimon.wealthpay.customer.domain.model.CustomerStatus;
import org.girardsimon.wealthpay.customer.infrastructure.db.repository.mapper.CustomerRowToStateMapper;
import org.girardsimon.wealthpay.customer.infrastructure.db.repository.mapper.CustomerToRowMapper;
import org.girardsimon.wealthpay.customer.jooq.tables.records.CustomerNationalityRecord;
import org.jooq.CommonTableExpression;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class CustomerRepository implements CustomerStore {

  private static final String ADMITTED_OUTCOME = "ADMITTED";

  // Code generation renamed the table constant to CUSTOMER_, the schema being named customer too.
  private static final org.girardsimon.wealthpay.customer.jooq.tables.Customer CUSTOMER =
      org.girardsimon.wealthpay.customer.jooq.tables.Customer.CUSTOMER_;

  // Aliased because both are read back off an untyped record, which resolves fields by name.
  private static final Field<String[]> NATIONALITY_CODES =
      field(
              select(arrayAgg(CUSTOMER_NATIONALITY.COUNTRY_CODE))
                  .from(CUSTOMER_NATIONALITY)
                  .where(CUSTOMER_NATIONALITY.CUSTOMER_ID.eq(CUSTOMER.ID)))
          .as("nationality_codes");

  private static final Field<Integer> TRANSITION_SEQUENCE_NO =
      field(
              select(coalesce(max(CUSTOMER_STATUS_TRANSITION.SEQUENCE_NO), 0))
                  .from(CUSTOMER_STATUS_TRANSITION)
                  .where(CUSTOMER_STATUS_TRANSITION.CUSTOMER_ID.eq(CUSTOMER.ID)))
          .as("transition_sequence_no");

  private final DSLContext dslContext;
  private final Clock clock;
  private final CustomerToRowMapper customerToRowMapper;
  private final CustomerRowToStateMapper customerRowToStateMapper;

  public CustomerRepository(
      DSLContext dslContext,
      Clock clock,
      CustomerToRowMapper customerToRowMapper,
      CustomerRowToStateMapper customerRowToStateMapper) {
    this.dslContext = dslContext;
    this.clock = clock;
    this.customerToRowMapper = customerToRowMapper;
    this.customerRowToStateMapper = customerRowToStateMapper;
  }

  // MANDATORY, not REQUIRED: three statements, and a customer that commits without its admission
  // link is the wrong retention anchor - the very state passing the decision id here prevents.
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void insert(Customer customer, AdmissionDecisionId admissionDecisionId) {
    insertCustomerRow(customer);
    insertNationalityRows(customer);
    insertAdmissionLink(customer, admissionDecisionId);
  }

  @Override
  public void linkAdmission(CustomerId customerId, AdmissionDecisionId admissionDecisionId) {
    int linked =
        dslContext
            .insertInto(
                CUSTOMER_ADMISSION,
                CUSTOMER_ADMISSION.CUSTOMER_ID,
                CUSTOMER_ADMISSION.DECISION_ID,
                CUSTOMER_ADMISSION.OUTCOME,
                CUSTOMER_ADMISSION.SUBJECT_TYPE)
            .select(
                select(
                        CUSTOMER.ID,
                        val(admissionDecisionId.id()),
                        inline(ADMITTED_OUTCOME),
                        CUSTOMER.KIND)
                    .from(CUSTOMER)
                    .where(CUSTOMER.ID.eq(customerId.id())))
            // Targeted at the pair: an untargeted DO NOTHING would also swallow a decision already
            // anchored to a different customer.
            .onConflict(CUSTOMER_ADMISSION.CUSTOMER_ID, CUSTOMER_ADMISSION.DECISION_ID)
            .doNothing()
            .execute();
    if (linked == 0 && !customerExists(customerId)) {
      throw new CustomerRowCorruptException(
          "An admission cannot be anchored: the customer row is absent");
    }
  }

  @Override
  public Optional<LoadedCustomer> load(CustomerId customerId) {
    return fetchCustomerRow(customerId)
        .map(
            row ->
                new LoadedCustomer(
                    Customer.reconstitute(toCustomerState(row)), row.get(TRANSITION_SEQUENCE_NO)));
  }

  @Override
  public Optional<CustomerState> findStateAfterSupersededTransition(CustomerId customerId) {
    return fetchCustomerRow(customerId).map(this::toCustomerState);
  }

  @Override
  public TransitionOutcome recordTransitionAndApply(
      LoadedCustomer loaded, CustomerStatus target, Instant occurredAt, Actor actor) {
    StatusTransition transition = loaded.transitionTo(target);
    return TransitionOutcome.ofUpdatedRows(
        switch (target) {
          case ACTIVE -> applyActivation(loaded, transition, occurredAt, actor);
          // Exhaustive, so a new status has to choose its own statement: the transition row
          // generalizes, the columns a status change implies do not.
          case ONBOARDING ->
              throw new IllegalStateException("ONBOARDING is never a status transition target");
        });
  }

  private int applyActivation(
      LoadedCustomer loaded, StatusTransition transition, Instant occurredAt, Actor actor) {
    UUID customerId = loaded.customer().getId().id();
    OffsetDateTime timestamp = OffsetDateTime.ofInstant(occurredAt, clock.getZone());
    CommonTableExpression<Record1<Integer>> transitionInsert =
        transitionInsert(customerId, loaded.sequenceNo(), transition, timestamp, actor);
    // No `AND status = from` here: a data-modifying CTE runs to completion whether or not the
    // primary query reads it, so a second predicate would commit the audit row while changing
    // nothing.
    return dslContext
        .with(transitionInsert)
        .update(CUSTOMER)
        .set(CUSTOMER.STATUS, transition.to().name())
        .set(CUSTOMER.ACTIVATED_AT, timestamp)
        .where(CUSTOMER.ID.eq(customerId).andExists(selectOne().from(transitionInsert)))
        .execute();
  }

  private CommonTableExpression<Record1<Integer>> transitionInsert(
      UUID customerId,
      int loadedSequenceNo,
      StatusTransition transition,
      OffsetDateTime occurredAt,
      Actor actor) {
    return name("transition_insert")
        .fields("inserted")
        .as(
            dslContext
                .insertInto(CUSTOMER_STATUS_TRANSITION)
                .set(CUSTOMER_STATUS_TRANSITION.CUSTOMER_ID, customerId)
                .set(CUSTOMER_STATUS_TRANSITION.SEQUENCE_NO, loadedSequenceNo + 1)
                .set(CUSTOMER_STATUS_TRANSITION.FROM_STATUS, transition.from().name())
                .set(CUSTOMER_STATUS_TRANSITION.TO_STATUS, transition.to().name())
                .set(CUSTOMER_STATUS_TRANSITION.OCCURRED_AT, occurredAt)
                .set(CUSTOMER_STATUS_TRANSITION.ACTOR, actor.value())
                .onConflict(
                    CUSTOMER_STATUS_TRANSITION.CUSTOMER_ID, CUSTOMER_STATUS_TRANSITION.SEQUENCE_NO)
                .doNothing()
                .returningResult(inline(1)));
  }

  private void insertCustomerRow(Customer customer) {
    int inserted;
    try {
      inserted =
          dslContext
              .insertInto(CUSTOMER)
              .set(customerToRowMapper.toCustomerRow(customer))
              .onConflict(CUSTOMER.EMAIL)
              .doNothing()
              .execute();
    } catch (DuplicateKeyException _) {
      // The email conflict is absorbed above, so what is left collided on an identifier we minted.
      // The cause is dropped: the driver spells it "Key (customer_number)=(...) already exists" and
      // the catch-all handler logs the whole chain.
      throw new CustomerNumberCollisionException();
    }
    if (inserted == 0) {
      throw new EmailAlreadyRegisteredException();
    }
  }

  private void insertNationalityRows(Customer customer) {
    List<CustomerNationalityRecord> rows = customerToRowMapper.toNationalityRows(customer);
    if (rows.isEmpty()) {
      return;
    }
    dslContext.batchInsert(rows).execute();
  }

  private void insertAdmissionLink(Customer customer, AdmissionDecisionId admissionDecisionId) {
    dslContext
        .insertInto(CUSTOMER_ADMISSION)
        .set(CUSTOMER_ADMISSION.CUSTOMER_ID, customer.getId().id())
        .set(CUSTOMER_ADMISSION.DECISION_ID, admissionDecisionId.id())
        .set(CUSTOMER_ADMISSION.OUTCOME, ADMITTED_OUTCOME)
        .set(CUSTOMER_ADMISSION.SUBJECT_TYPE, customer.getType().name())
        .execute();
  }

  private boolean customerExists(CustomerId customerId) {
    return dslContext.fetchExists(CUSTOMER, CUSTOMER.ID.eq(customerId.id()));
  }

  private Optional<Record> fetchCustomerRow(CustomerId customerId) {
    return Optional.ofNullable(
        dslContext
            .select(NATIONALITY_CODES, TRANSITION_SEQUENCE_NO)
            .select(CUSTOMER.fields())
            .from(CUSTOMER)
            .where(CUSTOMER.ID.eq(customerId.id()))
            .fetchOne());
  }

  private CustomerState toCustomerState(Record row) {
    return customerRowToStateMapper.toCustomerState(row.into(CUSTOMER), row.get(NATIONALITY_CODES));
  }
}
