/**
 * Decides whether an engagement has a pending template update, and over what version range.
 *
 * <p>Tenant plane: the input is one firm's projected engagement state. The logic here is pure,
 * so it can be reused unchanged by the read API and by the publish fan-out worker.
 *
 * <p>See {@code DESIGN.md}, section 1 (High-Level Architecture).
 */
package com.caseware.templateupdate.resolver;
