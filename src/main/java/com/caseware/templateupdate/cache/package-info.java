/**
 * Caching of change summaries by template version pair.
 *
 * <p>Global plane. The cache key deliberately contains no firm or engagement: a diff between two
 * template versions is identical for every customer, so one computation serves them all. That is
 * the difference between one model call per template update and one per engagement.
 *
 * <p>See {@code DESIGN.md}, section 1, and the no-customer-data invariant in section 5.
 */
package com.caseware.templateupdate.cache;
