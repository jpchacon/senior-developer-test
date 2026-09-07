/**
 * PostgreSQL-backed implementations of the storage ports.
 *
 * <p>This package, {@code api} and {@code config} are the only ones that know Spring exists. The
 * domain, resolver and summary packages stay framework-free so their rules can be tested with
 * plain constructors and no application context.
 *
 * <p>See {@code DESIGN.md}, section 1, for the schema layout and tenancy model.
 */
package com.caseware.templateupdate.persistence;
