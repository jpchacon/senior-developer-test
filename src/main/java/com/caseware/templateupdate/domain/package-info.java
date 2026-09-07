/**
 * Immutable value types shared by both planes of the system.
 *
 * <p>These types are deliberately free of Spring and persistence concerns so that the rules
 * expressed over them stay testable in isolation. Every record validates in its compact
 * constructor and defensively copies any collection it holds, so an invalid or mutable
 * instance cannot exist.
 *
 * <p>See {@code DESIGN.md}, section 1 (High-Level Architecture).
 */
package com.caseware.templateupdate.domain;
