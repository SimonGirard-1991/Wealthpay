package org.girardsimon.wealthpay.customer.infrastructure.db.repository;

import static org.girardsimon.wealthpay.customer.jooq.tables.AdmissionPolicy.ADMISSION_POLICY;
import static org.girardsimon.wealthpay.customer.jooq.tables.LicensedCountry.LICENSED_COUNTRY;
import static org.girardsimon.wealthpay.customer.jooq.tables.RestrictedCountry.RESTRICTED_COUNTRY;
import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.selectFrom;

import org.girardsimon.wealthpay.customer.application.CountryAdmissionPolicy;
import org.girardsimon.wealthpay.customer.domain.exception.AdmissionPolicyCorruptException;
import org.girardsimon.wealthpay.customer.domain.model.AdmissionPolicySnapshot;
import org.girardsimon.wealthpay.customer.infrastructure.db.repository.mapper.AdmissionPolicyRowToSnapshotMapper;
import org.girardsimon.wealthpay.customer.jooq.tables.records.LicensedCountryRecord;
import org.girardsimon.wealthpay.customer.jooq.tables.records.RestrictedCountryRecord;
import org.jooq.DSLContext;
import org.jooq.Record3;
import org.jooq.Result;
import org.springframework.stereotype.Repository;

@Repository
public class AdmissionPolicyRepository implements CountryAdmissionPolicy {

  private final DSLContext dslContext;
  private final AdmissionPolicyRowToSnapshotMapper admissionPolicyRowToSnapshotMapper;

  public AdmissionPolicyRepository(
      DSLContext dslContext,
      AdmissionPolicyRowToSnapshotMapper admissionPolicyRowToSnapshotMapper) {
    this.dslContext = dslContext;
    this.admissionPolicyRowToSnapshotMapper = admissionPolicyRowToSnapshotMapper;
  }

  @Override
  public AdmissionPolicySnapshot load() {
    // MULTISET rather than three round trips, and it must stay one statement: under READ COMMITTED
    // each statement takes its own snapshot, so separate reads could pair one version with another
    // version's rules and the recorded decision would document a policy that was never applied.
    Record3<Result<RestrictedCountryRecord>, Result<LicensedCountryRecord>, Long> row =
        dslContext
            .select(
                multiset(selectFrom(RESTRICTED_COUNTRY)).as("restrictions"),
                multiset(selectFrom(LICENSED_COUNTRY)).as("licences"),
                ADMISSION_POLICY.VERSION)
            .from(ADMISSION_POLICY)
            .fetchOne();
    if (row == null) {
      throw new AdmissionPolicyCorruptException("Admission policy table holds no version row");
    }
    return admissionPolicyRowToSnapshotMapper.toSnapshot(row.value1(), row.value2(), row.value3());
  }
}
