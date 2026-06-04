package org.girardsimon.wealthpay.customer.domain.model;

public sealed interface CustomerDetails permits IndividualDetails, CorporateDetails {}
