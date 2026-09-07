/**
 * Wires the framework-free core into the Spring application context.
 *
 * <p>The summarisation classes take their collaborators through constructors and know nothing
 * about Spring, so assembling them is an explicit decision made here rather than a side effect of
 * annotations scattered across the domain.
 */
package com.caseware.templateupdate.config;
